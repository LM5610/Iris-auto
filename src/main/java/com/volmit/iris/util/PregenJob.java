package com.volmit.iris.util;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.gui.PregenGui;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.DirectWorldWriter;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class PregenJob implements Listener
{
	private static PregenJob instance;
	private World world;
	private int size;
	private int total;
	private int genned;
	private boolean completed;
	public static int task = -1;
	private int ticks;
	private Semaphore working;
	private AtomicInteger g = new AtomicInteger();
	private PrecisionStopwatch s;
	private ChronoLatch cl;
	private ChronoLatch clx;
	private ChronoLatch clf;
	private MortarSender sender;
	private MultiBurst burst;
	private int mcaWidth;
	private int mcaX;
	private int mcaZ;
	private int chunkX;
	private int chunkZ;
	private Runnable onDone;
	private Spiraler spiraler;
	private Spiraler chunkSpiraler;
	private Spiraler preSpiraler;
	private boolean first;
	private static Consumer2<ChunkPosition, Color> consumer;
	private double cps = 0;
	private int lg = 0;
	private long lt = M.ms();
	private int cubeSize = 9;
	private long nogen = M.ms();
	private KList<ChunkPosition> requeueMCA = new KList<ChunkPosition>();
	private RollingSequence acps = new RollingSequence(PaperLib.isPaper() ? 8 : 32);
	private boolean paused = false;
	private long pausedAt = 0;
	private double pms = 0;
	private boolean gleaming = false;
	private final DirectWorldWriter writer;
	int xc = 0;
	private IrisAccess access = null;

	public PregenJob(World world, int size, MortarSender sender, Runnable onDone)
	{
		writer = new DirectWorldWriter(world.getWorldFolder());
		gleaming = (IrisSettings.get().isUseGleamPregenerator());
		g.set(0);
		burst = new MultiBurst(gleaming ? IrisSettings.get().getMaxAsyncChunkPregenThreads() : tc());
		instance = this;
		working = new Semaphore(gleaming ? IrisSettings.get().getMaxAsyncChunkPregenThreads() : tc());
		this.s = PrecisionStopwatch.start();
		Iris.instance.registerListener(this);
		this.world = world;
		this.size = size;
		this.onDone = onDone;
		this.sender = sender;
		cl = new ChronoLatch(3000);
		clx = new ChronoLatch(20000);
		clf = new ChronoLatch(30000);
		total = (size / 16) * (size / 16);
		genned = 0;
		mcaWidth = Math.floorDiv(size >> 4, cubeSize) + cubeSize;
		this.mcaX = 0;
		this.mcaZ = 0;
		this.chunkX = 0;
		this.chunkZ = 0;
		completed = false;
		first = true;

		chunkSpiraler = new Spiraler(cubeSize, cubeSize, (x, z) ->
		{
			chunkX = (mcaX * cubeSize) + x;
			chunkZ = (mcaZ * cubeSize) + z;
		});

		preSpiraler = new Spiraler(cubeSize, cubeSize, (x, z) ->
		{

		});

		spiraler = new Spiraler(mcaWidth, mcaWidth, (x, z) ->
		{
			mcaX = x;
			mcaZ = z;
			chunkSpiraler.retarget(cubeSize, cubeSize);
			ticks++;
		});

		chunkSpiraler.setOffset(Math.floorDiv(cubeSize, 2), Math.floorDiv(cubeSize, 2));

		if(task != -1)
		{
			stop();
		}

		if(IrisSettings.get().isLocalPregenGui())
		{
			PregenGui.launch(this);
		}

		fastFowardTicksIfPossible();
		task = Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::onTick, 0, 0);
	}

	public int tc()
	{
		return IrisSettings.get().maxAsyncChunkPregenThreads;
	}

	private IrisAccess access() {
		if(access != null)
		{
			return access;
		}

		access = IrisWorlds.access(world);

		return access;
	}

	public static void stop()
	{
		try
		{
			instance.writer.flush();
			Bukkit.getScheduler().cancelTask(task);

			if(consumer != null)
			{
				consumer.accept(new ChunkPosition(Integer.MAX_VALUE, Integer.MAX_VALUE), Color.pink);
			}
		}

		catch(Throwable e)
		{

		}
		task = -1;
	}

	public static void pause()
	{
		if(instance.paused)
		{
			return;
		}

		instance.pms = instance.s.getMilliseconds();
		instance.paused = true;
		instance.pausedAt = M.ms();
		instance.writer.flush();
	}

	public static void resume()
	{
		if(!instance.paused)
		{
			return;
		}

		instance.paused = false;
		instance.s.rewind(instance.pausedAt - M.ms());
	}

	public void onTick()
	{
		onTick(false);
	}

	public void onTick(boolean skip)
	{
		if(paused)
		{
			return;
		}

		if(completed)
		{
			return;
		}

		if(skip)
		{
			tick(skip);
		}

		PrecisionStopwatch p = PrecisionStopwatch.start();

		if(PaperLib.isPaper())
		{
			tickPaper(skip);
		}

		else
		{
			while(p.getMilliseconds() < 7000)
			{
				tick(skip);
			}
		}

		if(cl.flip())
		{
			tickMetrics();
		}
	}

	private void tickMetrics()
	{
		long eta = (long) ((total - genned) * (s.getMilliseconds() / (double) genned));
		String ss = "Pregen: " + Form.pc(Math.min((double) genned / (double) total, 1.0), 0) + ", Elapsed: " +

				Form.duration((long) (paused ? pms : s.getMilliseconds()))

				+ ", ETA: " + (genned >= total - 5 ? "Any second..." : s.getMilliseconds() < 25000 ? "Calculating..." : Form.duration(eta)) + " MS: " + Form.duration((s.getMilliseconds() / (double) genned), 2);
		Iris.info(ss);
		if(sender.isPlayer() && sender.player().isOnline())
		{
			sender.sendMessage(ss);
		}
	}

	public void tickPaper()
	{
		tickPaper(false);
	}

	public void tickPaper(boolean skip)
	{
		if(working.getQueueLength() >= tc())
		{
			return;
		}

		for(int i = 0; i < 64; i++)
		{
			tick(skip);
		}
	}

	public void tick()
	{
		tick(false);
	}

	public void tick(boolean skip)
	{
		if(M.ms() - nogen > 5000 && Math.min((double) genned / (double) total, 1.0) > 0.99 && !completed)
		{
			completed = true;

			for(Chunk i : world.getLoadedChunks())
			{
				i.unload(true);
			}

			saveAll();
			Iris.instance.unregisterListener(this);
			completed = true;
			sender.sendMessage("Pregen Completed!");
			if(consumer != null)
			{
				consumer.accept(new ChunkPosition(Integer.MAX_VALUE, Integer.MAX_VALUE), Color.pink);
			}
			onDone.run();
			return;
		}

		if(completed)
		{
			return;
		}

		if(first)
		{
			sender.sendMessage("Pregen Started for " + Form.f((size >> 4 >> 5 * size >> 4 >> 5)) + " Regions containing " + Form.f((size >> 4) * (size >> 4)) + " Chunks");
			first = false;
			spiraler.next();

			while(chunkSpiraler.hasNext())
			{
				chunkSpiraler.next();

				if(isChunkWithin(chunkX, chunkZ))
				{
					if(consumer != null)
					{
						consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.DARK_GRAY);
					}
				}
			}

			chunkSpiraler.retarget(cubeSize, cubeSize);
		}

		if(chunkSpiraler.hasNext())
		{
			chunkSpiraler.next();
			if(!skip)
			{
				tickChunk();
			}
		}

		else if(spiraler.hasNext() || requeueMCA.isNotEmpty())
		{
			if(!skip)
			{
				saveAllRequest();
			}

			if(requeueMCA.isNotEmpty())
			{
				ChunkPosition posf = requeueMCA.popRandom();
				mcaX = posf.getX();
				mcaZ = posf.getZ();
				chunkSpiraler.retarget(cubeSize, cubeSize);
			}

			else if(spiraler.hasNext())
			{
				spiraler.next();
			}

			while(chunkSpiraler.hasNext())
			{
				chunkSpiraler.next();

				if(isChunkWithin(chunkX, chunkZ) && consumer != null)
				{
					consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.BLACK.brighter());
				}
			}
			chunkSpiraler.retarget(cubeSize, cubeSize);
		}

		else if(!completed)
		{
			genned += (((size + 32) / 16) * (size + 32) / 16) + 100000;
		}

		double dur = M.ms() - lt;

		if(dur > 1000 && genned > lg)
		{
			int gain = genned - lg;
			double rat = dur / 1000D;
			acps.put((double) gain / rat);
			cps = acps.getAverage();
			lt = M.ms();
			lg = genned;
		}
	}

	private void tickChunk()
	{
		tickSyncChunk();
	}

	private void tickSyncChunk()
	{
		if(isChunkWithin(chunkX, chunkZ))
		{
			if(PaperLib.isPaper())
			{
				int cx = chunkX;
				int cz = chunkZ;

				if(gleaming)
				{
					if(consumer != null)
					{
						consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.cyan.darker().darker().darker());
					}

					Runnable g = () -> {
						try {
							working.acquire();
							if(consumer != null)
							{
								consumer.accept(new ChunkPosition(cx, cz), Color.cyan);
							}
							int xx = cx;
							int zz = cz;

							if(IrisSettings.get().isUseExperimentalGleamMCADirectWriteMode())
							{
								access().directWriteChunk(world, cx, cz, writer);
							}

							else
							{
								access().generatePaper(world, cx, cz);
							}

							working.release();
							genned++;
							nogen = M.ms();

							if(consumer != null)
							{
								if(IrisSettings.get().isUseExperimentalGleamMCADirectWriteMode())
								{
									consumer.accept(new ChunkPosition(xx, zz), Color.blue);

								}

								else {
									consumer.accept(new ChunkPosition(xx, zz), Color.yellow);
								}
							}
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					};

					J.a(g);
				}

				else
				{if(consumer != null)
				{
					consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.magenta.darker().darker().darker());
				}
					J.a(() ->
					{
						try
						{
							working.acquire();

							if(consumer != null)
							{
								consumer.accept(new ChunkPosition(cx, cz), Color.magenta);
							}

							Chunk chunk =  PaperLib.getChunkAtAsync(world, cx, cz, true, true).join();
							working.release();
							genned++;
							nogen = M.ms();

							if(consumer != null)
							{
								consumer.accept(new ChunkPosition(chunk.getX(), chunk.getZ()), Color.green);
							}
						}

						catch(InterruptedException e)
						{
							e.printStackTrace();
						}
					});
				}
			}

			else
			{
				if(consumer != null)
				{
					consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.black.brighter());
				}

				world.loadChunk(chunkX, chunkZ);
				genned++;
				nogen = M.ms();

				if(consumer != null)
				{
					consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.blue);
				}
			}
		}

		else
		{
			if(consumer != null)
			{
				consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.blue.brighter().brighter());
			}
		}
	}

	public void fastFowardTicksIfPossible()
	{
		try
		{
			int ticks = Integer.valueOf(IO.readAll(new File(world.getWorldFolder(), "pregen.ticks")).trim());
			ticks -= 6;

			if(ticks <= 0)
			{
				return;
			}

			for(int i = 0; i < ticks; i++)
			{
				spiraler.next();
			}
		}

		catch(Throwable e)
		{

		}
	}

	public void saveAllRequest()
	{
		if(clf.flip())
		{
			for(Chunk i : world.getLoadedChunks())
			{
				world.unloadChunkRequest(i.getX(), i.getZ());
			}

			J.a(() ->
			{
				try
				{
					IO.writeAll(new File(world.getWorldFolder(), "pregen.ticks"), ticks + "");
				}

				catch(IOException e)
				{
					e.printStackTrace();
				}
			});
		}

		if(clx.flip())
		{
			saveAll();
		}
	}

	@EventHandler
	public void on(ChunkUnloadEvent e)
	{
		try
		{
			if(e.getWorld().equals(world) && isChunkWithin(e.getChunk().getX(), e.getChunk().getZ()) && consumer != null)
			{
				consumer.accept(new ChunkPosition(e.getChunk().getX(), e.getChunk().getZ()), Color.blue.darker());
			}
		}

		catch(Throwable ex)
		{

		}
	}

	public void saveAll()
	{
		for(Chunk i : world.getLoadedChunks())
		{
			world.unloadChunkRequest(i.getX(), i.getZ());
		}

		if(IrisSettings.get().isSaveAllDuringPregen())
		{
			world.save();
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
		}

		writer.flush();
	}

	public int max()
	{
		return size / 2;
	}

	public int min()
	{
		return -max();
	}

	public boolean isChunkWithin(int x, int z)
	{
		return !(Math.abs(x << 4) > Math.floorDiv(size, 2) + 16 || Math.abs(z << 4) > Math.floorDiv(size, 2) + 16);
	}

	public void subscribe(Consumer2<ChunkPosition, Color> s)
	{
		consumer = s;
	}

	public String[] getProgress()
	{
		long eta = (long) ((total - genned) * 1000D / cps);

		KList<String> vv = new KList<String>( new String[] {"Progress:  " + Form.pc(Math.min((double) genned / (double) total, 1.0), 0),
				  "Generated:   " + Form.f(genned) + " Chunks",
				  "Remaining:   " + Form.f(total - genned) + " Chunks",
				  "Elapsed:     " + Form.duration((long) (paused ? pms : s.getMilliseconds()), 2),
				  "Estimate:    " + ((genned >= total - 5 ? "Any second..." : s.getMilliseconds() < 25000 ? "Calculating..." : Form.duration(eta, 2))),
				  "ChunksMS:    " + Form.duration(1000D / cps, 2),
				  "Chunks/s:    " + Form.f(cps, 1),
		});

		try
		{
			vv.add("Parallelism: " + access().getCompound().getCurrentlyGeneratingEngines());
			vv.add("Precache   : " + access().getPrecacheSize());
		}

		catch(Throwable e)
		{

		}

		return vv.toArray(new String[vv.size()]);
	}

	public static void pauseResume()
	{
		if(instance.paused)
		{
			resume();
		}

		else
		{
			pause();
		}
	}

	public static boolean isPaused()
	{
		return instance.paused;
	}

	public boolean paused()
	{
		return paused;
	}
}