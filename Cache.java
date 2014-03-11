package org.lru.cache;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Represent a thread-safe and size based(LRU, categories divided) cache.
 * 
 * The removing policy involved two mechanism: <br>
 * 1. Priorities(Categories)    <br>
 * 2. LRU (least recently used) <br>
 * 
 * The cache holds specific dedicated data structure for each category/priority object.
 * In other words, if for example the lowest priority cache reaches its limit
 * the eldest entry with the lowest priority will be removed and so other
 * priorities level respectively.
 * 
 * @param <K> 			- Represents the cache Key type.
 * @param <V> 			- Represents the cache Value type.
 * @param <Priorities>	- Represents the cache Categories enumeration.
 * 
 * @author pazinio
 */
public class Cache<K, V, Priorities extends Enum<Priorities>> {

	/** LRUCache for each priority object level */
	private Map<Priorities, Map<K, V>> priorityCaches;

	/** Cache accessories */
	private final Priorities [] priorities;


	/**
	 * capacities    - determine whether an entry should be evicted from the cache
	 * priorities  - the lowest priority level
	 */
	public Cache(int []capacities, Class<Priorities> p) {

		Priorities[] enumConstants = p.getEnumConstants();
		
		if (enumConstants.length != capacities.length)
			throw new IllegalArgumentException("enumConstants.length != capacities.length");

		
		this.priorities     = enumConstants;
		
		// Concurrency is not needed here(container map), since after the first creation only
		// get by key method will be invoked (no structural modification)
		this.priorityCaches = new HashMap<Priorities, Map<K, V>>(enumConstants.length); 

		int i=0;
		for (Priorities priorityEnum : priorities) {
			if (capacities[i] < 0) throw new IllegalArgumentException();
			priorityCaches.put(priorityEnum, Collections.synchronizedMap(new LRUCache<K, V>(capacities[i])));
			i++;
		}
	}

	public void put(K k, V v, Priorities priority) {
		addEntry(k, v, priority);
	}


	public V get(K k) {
		for (Priorities priorityEnum : priorities) {
			V v = getValue(k, priorityEnum);
			if (v != null) {
				return v;
			}
		}

		return null;
	}

	private V getValue(K k, Priorities priority) {
		return priorityCaches.get(priority).get(k);
	}

	private void addEntry(K k, V v, Priorities priority) {
		Map<K, V> map = priorityCaches.get(priority);
		map.put(k, v);
	}
	
	//Debug Only(Package-private)
	synchronized int size(){
		int size=0;
		
		for (Priorities priority: priorities) {
			size += priorityCaches.get(priority).size();
		}
		
		return size;
	}

	//Debug Only
	@Override
	synchronized public String toString() {
		return "Cache [priorityCaches=" + priorityCaches + ", priorities="
				+ Arrays.toString(priorities) + "]";
	}

	
}
