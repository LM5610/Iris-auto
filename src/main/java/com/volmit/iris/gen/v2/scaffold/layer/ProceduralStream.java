package com.volmit.iris.gen.v2.scaffold.layer;

import java.util.List;
import java.util.function.Function;

import com.volmit.iris.gen.v2.scaffold.Hunk;
import com.volmit.iris.gen.v2.scaffold.Significance;
import com.volmit.iris.gen.v2.scaffold.stream.AddingStream;
import com.volmit.iris.gen.v2.scaffold.stream.AwareConversionStream2D;
import com.volmit.iris.gen.v2.scaffold.stream.AwareConversionStream3D;
import com.volmit.iris.gen.v2.scaffold.stream.CachedConversionStream;
import com.volmit.iris.gen.v2.scaffold.stream.CachedStream2D;
import com.volmit.iris.gen.v2.scaffold.stream.ClampedStream;
import com.volmit.iris.gen.v2.scaffold.stream.ConversionStream;
import com.volmit.iris.gen.v2.scaffold.stream.DividingStream;
import com.volmit.iris.gen.v2.scaffold.stream.FittedStream;
import com.volmit.iris.gen.v2.scaffold.stream.ForceDoubleStream;
import com.volmit.iris.gen.v2.scaffold.stream.FunctionStream;
import com.volmit.iris.gen.v2.scaffold.stream.Interpolated;
import com.volmit.iris.gen.v2.scaffold.stream.ModuloStream;
import com.volmit.iris.gen.v2.scaffold.stream.MultiplyingStream;
import com.volmit.iris.gen.v2.scaffold.stream.OffsetStream;
import com.volmit.iris.gen.v2.scaffold.stream.RoundingDoubleStream;
import com.volmit.iris.gen.v2.scaffold.stream.RoundingStream;
import com.volmit.iris.gen.v2.scaffold.stream.SelectionStream;
import com.volmit.iris.gen.v2.scaffold.stream.SignificanceStream;
import com.volmit.iris.gen.v2.scaffold.stream.SubtractingStream;
import com.volmit.iris.gen.v2.scaffold.stream.To3DStream;
import com.volmit.iris.gen.v2.scaffold.stream.ZoomStream;
import com.volmit.iris.util.Function2;
import com.volmit.iris.util.Function3;
import com.volmit.iris.util.Function4;

public interface ProceduralStream<T> extends ProceduralLayer, Interpolated<T>
{
	public static ProceduralStream<Double> ofDouble(Function2<Double, Double, Double> f)
	{
		return of(f, Interpolated.DOUBLE);
	}

	public static ProceduralStream<Double> ofDouble(Function3<Double, Double, Double, Double> f)
	{
		return of(f, Interpolated.DOUBLE);
	}

	public static <T> ProceduralStream<T> of(Function2<Double, Double, T> f, Interpolated<T> helper)
	{
		return of(f, (x, y, z) -> f.apply(x, z), helper);
	}

	public static <T> ProceduralStream<T> of(Function3<Double, Double, Double, T> f, Interpolated<T> helper)
	{
		return of((x, z) -> f.apply(x, 0D, z), f, helper);
	}

	public static <T> ProceduralStream<T> of(Function2<Double, Double, T> f, Function3<Double, Double, Double, T> f2, Interpolated<T> helper)
	{
		return new FunctionStream<>(f, f2, helper);
	}

	default ProceduralStream<T> add(Function3<Double, Double, Double, Double> a)
	{
		return new AddingStream<>(this, a);
	}

	default ProceduralStream<T> add(Function2<Double, Double, Double> a)
	{
		return new AddingStream<>(this, a);
	}

	default ProceduralStream<T> add(double a)
	{
		return new AddingStream<>(this, a);
	}

	default ProceduralStream<T> subtract(Function3<Double, Double, Double, Double> a)
	{
		return new SubtractingStream<>(this, a);
	}

	default ProceduralStream<T> subtract(Function2<Double, Double, Double> a)
	{
		return new SubtractingStream<>(this, a);
	}

	default ProceduralStream<T> subtract(double a)
	{
		return new SubtractingStream<>(this, a);
	}

	default ProceduralStream<T> multiply(Function3<Double, Double, Double, Double> a)
	{
		return new MultiplyingStream<>(this, a);
	}

	default ProceduralStream<T> multiply(Function2<Double, Double, Double> a)
	{
		return new MultiplyingStream<>(this, a);
	}

	default ProceduralStream<T> multiply(double a)
	{
		return new MultiplyingStream<>(this, a);
	}

	default ProceduralStream<T> divide(Function3<Double, Double, Double, Double> a)
	{
		return new DividingStream<>(this, a);
	}

	default ProceduralStream<T> divide(Function2<Double, Double, Double> a)
	{
		return new DividingStream<>(this, a);
	}

	default ProceduralStream<T> divide(double a)
	{
		return new DividingStream<>(this, a);
	}

	default ProceduralStream<T> modulo(Function3<Double, Double, Double, Double> a)
	{
		return new ModuloStream<>(this, a);
	}

	default ProceduralStream<T> modulo(Function2<Double, Double, Double> a)
	{
		return new ModuloStream<>(this, a);
	}

	default ProceduralStream<T> modulo(double a)
	{
		return new ModuloStream<>(this, a);
	}

	default ProceduralStream<Integer> round()
	{
		return new RoundingStream(this);
	}

	default ProceduralStream<Double> roundDouble()
	{
		return new RoundingDoubleStream(this);
	}

	default ProceduralStream<T> forceDouble()
	{
		return new ForceDoubleStream<T>(this);
	}

	default ProceduralStream<Significance<T>> significance(double radius, int checks)
	{
		return new SignificanceStream<Significance<T>, T>(this, radius, checks);
	}

	default ProceduralStream<T> to3D()
	{
		return new To3DStream<T>(this);
	}

	default ProceduralStream<T> cache2D(int maxSize)
	{
		return new CachedStream2D<T>(this, maxSize);
	}

	default <V> ProceduralStream<V> convert(Function<T, V> converter)
	{
		return new ConversionStream<T, V>(this, converter);
	}

	default <V> ProceduralStream<V> convertAware2D(Function3<T, Double, Double, V> converter)
	{
		return new AwareConversionStream2D<T, V>(this, converter);
	}

	default <V> ProceduralStream<V> convertAware3D(Function4<T, Double, Double, Double, V> converter)
	{
		return new AwareConversionStream3D<T, V>(this, converter);
	}

	default <V> ProceduralStream<V> convertCached(Function<T, V> converter)
	{
		return new CachedConversionStream<T, V>(this, converter);
	}

	default ProceduralStream<T> offset(double x, double y, double z)
	{
		return new OffsetStream<T>(this, x, y, z);
	}

	default ProceduralStream<T> offset(double x, double z)
	{
		return new OffsetStream<T>(this, x, 0, z);
	}

	default ProceduralStream<T> zoom(double x, double y, double z)
	{
		return new ZoomStream<T>(this, x, y, z);
	}

	default ProceduralStream<T> zoom(double x, double z)
	{
		return new ZoomStream<T>(this, x, 1, z);
	}

	default ProceduralStream<T> zoom(double all)
	{
		return new ZoomStream<T>(this, all, all, all);
	}

	default <V> ProceduralStream<V> select(@SuppressWarnings("unchecked") V... types)
	{
		return new SelectionStream<V>(this, types);
	}

	default <V> ProceduralStream<V> select(List<V> types)
	{
		return new SelectionStream<V>(this, types);
	}

	default ProceduralStream<T> clamp(double min, double max)
	{
		return new ClampedStream<T>(this, min, max);
	}

	default ProceduralStream<T> fit(double min, double max)
	{
		return new FittedStream<T>(this, min, max);
	}

	default ProceduralStream<T> fit(double inMin, double inMax, double min, double max)
	{
		return new FittedStream<T>(this, inMin, inMax, min, max);
	}

	default void fill(Hunk<T> h, double x, double y, double z)
	{
		for(int i = 0; i < h.getWidth(); i++)
		{
			for(int j = 0; j < h.getHeight(); j++)
			{
				for(int k = 0; k < h.getDepth(); k++)
				{
					h.set(i, j, k, get(i + x, j + y, k + z));
				}
			}
		}
	}

	default <V> void fill2D(Hunk<V> h, double x, double z, V v)
	{
		for(int i = 0; i < h.getWidth(); i++)
		{
			for(int k = 0; k < h.getDepth(); k++)
			{
				double n = getDouble(i + x, k + z);

				for(int j = 0; j < Math.min(h.getHeight(), n); j++)
				{
					h.set(i, j, k, v);
				}
			}
		}
	}

	default <V> void fill3D(Hunk<V> h, double x, int y, double z, V v)
	{
		for(int i = 0; i < h.getWidth(); i++)
		{
			for(int k = 0; k < h.getDepth(); k++)
			{

				for(int j = 0; j < h.getHeight(); j++)
				{
					double n = getDouble(i + x, j + y, k + z);

					if(n >= 0.5)
					{
						h.set(i, j, k, v);
					}
				}
			}
		}
	}

	default <V> void fill3D(Hunk<V> h, double x, int y, double z, ProceduralStream<V> v)
	{
		for(int i = 0; i < h.getWidth(); i++)
		{
			for(int k = 0; k < h.getDepth(); k++)
			{
				for(int j = 0; j < h.getHeight(); j++)
				{
					double n = getDouble(i + x, j + y, k + z);

					if(n >= 0.5)
					{
						h.set(i, j, k, v.get(i + x, j + y, k + z));
					}
				}
			}
		}
	}

	default <V> void fill2D(Hunk<V> h, double x, double z, ProceduralStream<V> v)
	{
		for(int i = 0; i < h.getWidth(); i++)
		{
			for(int k = 0; k < h.getDepth(); k++)
			{
				double n = getDouble(i + x, k + z);

				for(int j = 0; j < Math.min(h.getHeight(), n); j++)
				{
					h.set(i, j, k, v.get(i + x, j, k + z));
				}
			}
		}
	}

	public T get(double x, double z);

	public T get(double x, double y, double z);

	default double getDouble(double x, double z)
	{
		return toDouble(get(x, z));
	}

	default double getDouble(double x, double y, double z)
	{
		return toDouble(get(x, y, z));
	}
}