package com.emc.fapi.utils.cache;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

/**
 * 
 * LRU Cache Implementation.
 * 
 * LRU stands for "Least Recently Used", which refers to the policy of removing
 * the oldest, or least-recently used entries to make space for new data.
 * 
 * LRU caches have a maximum number of data items that they will hold and these
 * items are usually arranged in a list. When an item is added to the cache, and
 * every time it is accessed after that, it is automatically moved to the head
 * of the list. If the cache is full and a slot is required for a new item, the
 * cache makes room by discarding the entry at the tail of the list - 
 *  the least-recently used item.
 * 
 * @author pazinio
 * 
 * @param <K>
 * @param <V>
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {

	private final int capacity;

	/**
	 * Note that the constructor takes as an argument the maximum number of
	 * entries we want a Cache object to hold. The superclass constructor has
	 * three arguments: the initial capacity of the map, the load factor, and a
	 * boolean argument that tells the LinkedHashMap constructor to keep entries
	 * in access order instead of the default insertion order. (See the
	 * java.util.HashMap API documentation for a description of the initial
	 * capacity and load factor.) In this case we set the initial capacity to be
	 * one more than our required cache size - this is because new entries are
	 * added before any are removed, so for example if we want to hold 100
	 * entries the cache will actually contain 101 for an instant when new data
	 * is added. Setting the load factor to 1.1 ensures that the rehashing
	 * mechanism of the underlying HashMap class isn't triggered - this isn't a
	 * vital point but helps a little with efficiency at run-time.
	 * 
	 * The removeEldestEntry() method overrides a default implementation in
	 * LinkedHashMap and is where we determine the policy for removing the
	 * oldest entry. In this case, we return true when the cache has more
	 * entries than our defined capacity.
	 * 
	 * @param capacity
	 */
	public LRUCache(int capacity) {
		super(capacity + 1, 1.1f, true);
		this.capacity = capacity;
	}

	/**
	 * @param eldest
	 *            The least recently accessed entry in the map. This is the
	 *            entry that will be removed in case this method returns
	 *            <tt>true</tt>.
	 * 
	 *            If the map was empty prior to the <tt>put</tt> or
	 *            <tt>putAll</tt> invocation resulting in this invocation, this
	 *            will be the entry that was just inserted; in other words, if
	 *            the map contains a single entry, the eldest entry is also the
	 *            newest.
	 * 
	 * @return <tt>true</tt> if the eldest entry should be removed from the map;
	 *         <tt>false</tt> if it should be retained.
	 */
	protected boolean removeEldestEntry(Entry<K, V> eldest) {
		return size() > capacity;
	}

	private static final long serialVersionUID = 1L;
}
