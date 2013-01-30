/*
 *  Copyright (c) 2012 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.malhartech.lib.math;

import com.malhartech.annotation.InputPortFieldAnnotation;
import com.malhartech.annotation.OutputPortFieldAnnotation;
import com.malhartech.api.DefaultInputPort;
import com.malhartech.api.DefaultOutputPort;
import com.malhartech.lib.util.BaseNumberKeyValueOperator;
import com.malhartech.lib.util.UnifierHashMapInteger;
import com.malhartech.lib.util.UnifierHashMapSumKeys;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableInt;

/**
 *
 * Emits the sum, and count of values for each key at the end of window. <p>
 * This is an end of window operator<br>
 * <br>
 * <b>Ports</b>:<br>
 * <b>data</b>: expects Map&lt;K,V extends Number&gt;<br>
 * <b>sum</b>: emits HashMap&lt;K,V&gt;<br>
 * <b>count</b>: emits HashMap&lt;K,Integer&gt;</b><br>
 * <br>
 * <b>Properties</b>:<br>
 * <b>inverse</b>: if set to true the key in the filter will block tuple<br>
 * <b>filterBy</b>: List of keys to filter on<br>
 * <b>cumulative</b>: boolean flag, if set the sum is not cleared at the end of window, <br>
 * hence generating cumulative sum across streaming windows. Default is false.<br>
 * <br>
 * <b>Specific compile time checks</b>: None<br>
 * <b>Specific run time checks</b>: None<br>
 * <p>
 * <b>Benchmarks</b>: Blast as many tuples as possible in inline mode<br>
 * <table border="1" cellspacing=1 cellpadding=1 summary="Benchmark table for SumCountMap&lt;K,V extends Number&gt; operator template">
 * <tr><th>In-Bound</th><th>Out-bound</th><th>Comments</th></tr>
 * <tr><td><b>18 Million K,V pairs/s</b></td><td>One K,V or K,Integer per key per window per port</td><td>In-bound rate is the main determinant of performance. Tuples are assumed to be
 * immutable. If you use mutable tuples and have lots of keys, the benchmarks may be lower</td></tr>
 * </table><br>
 * <p>
 * <b>Function Table (K=String, V=Integer)</b>:
 * <table border="1" cellspacing=1 cellpadding=1 summary="Function table for SumCountMap&lt;K,V extends Number&gt; operator template">
 * <tr><th rowspan=2>Tuple Type (api)</th><th>In-bound (<i>data</i>::process)</th><th colspan=3>Out-bound (emit)</th></tr>
 * <tr><th><i>data</i>(Map&lt;K,V&gt;)</th><th><i>sum</i>(HashMap&lt;K,V&gt;)</th><th><i>count</i>(HashMap&lt;K,Integer&gt;)</th></tr>
 * <tr><td>Begin Window (beginWindow())</td><td>N/A</td><td>N/A</td><td>N/A</td></tr>
 * <tr><td>Data (process())</td><td>{a=2,b=20,c=1000}</td><td></td><td></td></tr>
 * <tr><td>Data (process())</td><td>{a=1}</td><td></td><td></td></tr>
 * <tr><td>Data (process())</td><td>{a=10,b=5}</td><td></td><td></td></tr>
 * <tr><td>Data (process())</td><td>{d=55,b=12}</td><td></td><td></td></tr>
 * <tr><td>Data (process())</td><td>{d=22}</td><td></td><td></td></tr>
 * <tr><td>Data (process())</td><td>{d=14}</td><td></td><td></td></tr>
 * <tr><td>Data (process())</td><td>{d=46,e=2}</td><td></td><td></td></tr>
 * <tr><td>Data (process())</td><td>{d=4,a=23}</td><td></td><td></td></tr>
 * <tr><td>End Window (endWindow())</td><td>N/A</td><td>{a=36,b=37,c=1000,d=141,e=2}</td><td>{a=4,b=3,c=1,d=5,e=1}</td></tr>
 * </table>
 * <br>
 *
 * @author Amol Kekre (amol@malhar-inc.com)<br>
 * <br>
 */
public class SumCountMap<K, V extends Number> extends BaseNumberKeyValueOperator<K, V>
{
  /**
   * Input port to receive data.
   */
  @InputPortFieldAnnotation(name = "data")
  public final transient DefaultInputPort<Map<K, V>> data = new DefaultInputPort<Map<K, V>>(this)
  {
    /**
     * For each tuple (a HashMap of keys,val pairs)
     * Adds the values for each key,
     * Counts the number of occurrences of each key
     */
    @Override
    public void process(Map<K, V> tuple)
    {
      for (Map.Entry<K, V> e: tuple.entrySet()) {
        K key = e.getKey();
        if (!doprocessKey(key)) {
          continue;
        }
        if (sum.isConnected()) {
          MutableDouble val = sums.get(key);
          if (val == null) {
            val = new MutableDouble(e.getValue().doubleValue());
          }
          else {
            val.add(e.getValue().doubleValue());
          }
          sums.put(cloneKey(key), val);
        }
        if (count.isConnected()) {
          MutableInt count = counts.get(key);
          if (count == null) {
            count = new MutableInt(0);
            counts.put(cloneKey(key), count);
          }
          count.increment();
        }
      }
    }
  };
  @OutputPortFieldAnnotation(name = "sum", optional = true)
  public final transient DefaultOutputPort<HashMap<K, V>> sum = new DefaultOutputPort<HashMap<K, V>>(this)
  {
    @Override
    public Unifier<HashMap<K, V>> getUnifier()
    {
      return new UnifierHashMapSumKeys<K, V>();
    }
  };

  @OutputPortFieldAnnotation(name = "sumDouble", optional = true)
  public final transient DefaultOutputPort<HashMap<K, Double>> sumDouble = new DefaultOutputPort<HashMap<K, Double>>(this)
  {
    @Override
    public Unifier<HashMap<K, Double>> getUnifier()
    {
      UnifierHashMapSumKeys ret = new UnifierHashMapSumKeys<K, Double>();
      ret.setType(Double.class);
      return ret;
    }
  };

  @OutputPortFieldAnnotation(name = "sumInteger", optional = true)
  public final transient DefaultOutputPort<HashMap<K, Integer>> sumInteger = new DefaultOutputPort<HashMap<K, Integer>>(this)
  {
    @Override
    public Unifier<HashMap<K, Integer>> getUnifier()
    {
      UnifierHashMapSumKeys ret = new UnifierHashMapSumKeys<K, Integer>();
      ret.setType(Integer.class);
      return ret;
    }
  };

  @OutputPortFieldAnnotation(name = "sumLong", optional = true)
  public final transient DefaultOutputPort<HashMap<K, Long>> sumLong = new DefaultOutputPort<HashMap<K, Long>>(this)
  {
    @Override
    public Unifier<HashMap<K, Long>> getUnifier()
    {
      UnifierHashMapSumKeys ret = new UnifierHashMapSumKeys<K, Long>();
      ret.setType(Long.class);
      return ret;
    }
  };

  @OutputPortFieldAnnotation(name = "sumShort", optional = true)
  public final transient DefaultOutputPort<HashMap<K, Short>> sumShort = new DefaultOutputPort<HashMap<K, Short>>(this)
  {
    @Override
    public Unifier<HashMap<K, Short>> getUnifier()
    {
      UnifierHashMapSumKeys ret = new UnifierHashMapSumKeys<K, Short>();
      ret.setType(Short.class);
      return ret;
    }
  };
  @OutputPortFieldAnnotation(name = "sumFloat", optional = true)
  public final transient DefaultOutputPort<HashMap<K, Float>> sumFloat = new DefaultOutputPort<HashMap<K, Float>>(this)
  {
    @Override
    public Unifier<HashMap<K, Float>> getUnifier()
    {
      UnifierHashMapSumKeys ret = new UnifierHashMapSumKeys<K, Float>();
      ret.setType(Float.class);
      return ret;
    }
  };

  @OutputPortFieldAnnotation(name = "count", optional = true)
  public final transient DefaultOutputPort<HashMap<K, Integer>> count = new DefaultOutputPort<HashMap<K, Integer>>(this)
  {
    @Override
    public Unifier<HashMap<K, Integer>> getUnifier()
    {
      return new UnifierHashMapInteger<K>();
    }
  };
  protected transient HashMap<K, MutableDouble> sums = new HashMap<K, MutableDouble>();
  protected transient HashMap<K, MutableInt> counts = new HashMap<K, MutableInt>();
  protected boolean cumulative = false;

  public boolean isCumulative()
  {
    return cumulative;
  }

  public void setCumulative(boolean cumulative)
  {
    this.cumulative = cumulative;
  }

  /**
   * Emits on all ports that are connected. Data is precomputed during process on input port
   * endWindow just emits it for each key
   * Clears the internal data before return
   */
  @Override
  public void endWindow()
  {

    // Should allow users to send each key as a separate tuple to load balance
    // This is an aggregate node, so load balancing would most likely not be needed

    HashMap<K, V> tuples = new HashMap<K, V>();
    HashMap<K, Integer> ctuples = new HashMap<K, Integer>();
    HashMap<K, Double> dtuples = new HashMap<K, Double>();
    HashMap<K, Integer> ituples = new HashMap<K, Integer>();
    HashMap<K, Float> ftuples = new HashMap<K, Float>();
    HashMap<K, Long> ltuples = new HashMap<K, Long>();
    HashMap<K, Short> stuples = new HashMap<K, Short>();

    for (Map.Entry<K, MutableDouble> e: sums.entrySet()) {
      K key = e.getKey();
      MutableDouble val = e.getValue();
      tuples.put(key, getValue(val.doubleValue()));
      dtuples.put(key, val.doubleValue());
      ituples.put(key, val.intValue());
      ftuples.put(key, val.floatValue());
      ltuples.put(key, val.longValue());
      stuples.put(key, val.shortValue());
      ctuples.put(key, counts.get(e.getKey()).toInteger());
    }

    sum.emit(tuples);
    sumDouble.emit(dtuples);
    sumInteger.emit(ituples);
    sumLong.emit(ltuples);
    sumShort.emit(stuples);
    sumFloat.emit(ftuples);
    count.emit(ctuples);
    clearCache();
  }

  public void clearCache()
  {
    if (!cumulative) {
      sums.clear();
      counts.clear();
    }
  }
}
