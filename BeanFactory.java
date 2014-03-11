package org.lru.cache;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.log4j.Logger;

/**
 * This container class (singleton) keeps Java beans using LRU cache (categories divided).
 * Basically the BeanFactory purpose is to prevent massive allocation of the same
 * bean.
 * 
 * Each bean identified by a Context. Depending on the specific bean definition,
 * get method could be blocking in case another client(thread) is looking for
 * the same bean object at the same time, The lock continues while the first
 * client doesn't ends the bean populations.
 * 
 * @author pazinio
 */
final public class BeanFactory {

	// Constants
	private static final Configuration CONFIG	= Configuration.instance();
	private static final int     LOCKS 		= CONFIG.getCacheLocksNum();
	private static final int []  CACHE_CAPACITIES	= CONFIG.getCacheCapacities();
	private static final boolean CACHE_STATISTICS 	= CONFIG.getCacheStatistics();
	private static final Logger logger		= Logger.getLogger(BeanFactory.class);
	private static final Object NOT_POPULATED 	= null;

	// Members
	final private Cache<Command<?>, BeanWrapper, Priorities> cache;
	final private BeanToContextBindings bindings;
	final private Object syncLocks[];


	//Private C'tor
	private BeanFactory() {
		cache = new Cache<Command<?>, BeanWrapper, BeanFactory.Priorities>(
				CACHE_CAPACITIES, Priorities.class);
		
		
		syncLocks = new Object[LOCKS];
		for (int i = 0; i < syncLocks.length; i++) {
			syncLocks[i] = new Object();
		}
		
		bindings = BeanToContextBindings.getInstance();
		
		printCahceCapacities();
	}

	// The Only Public method !
	final public Object get(Command<?> command, Context context, BeanPopulator<?> populate, boolean forcedPopulate){
		accessCount.incrementAndGet();
		
		Object bean = NOT_POPULATED; 
		
		//In case forcedRefresh is set bean will not be retrieved from cache (even if it exists there),
		//but cache will be updated with the latest bean at any case
		if (!forcedPopulate){ 
			bean = getBeanFromCache(command, context);
		}
		
		//Double-checked locking
		if (bean == NOT_POPULATED) {
			bean = populateBean(command, context, populate, forcedPopulate);
		}
		
		if (CACHE_STATISTICS == true)
			printCahceStatistics();
		
		return bean;
	}


	// Private methods
	private Object getBeanFromCache(Command<?> command, Context context) {
		BeanWrapper cacheObjectWrapper = cache.get(command);

		if (isValidWrapperAndContext(context, cacheObjectWrapper)) {
			Object value = cacheObjectWrapper.value;
			marksBeanFoundInCache(value);			
			return value;
		}

		return null;
	}

	private boolean isValidWrapperAndContext(Context context, BeanWrapper cacheObjectWrapper) {
		if (context == null){
			nullContext.incrementAndGet();
			return false;
		}
		
		if (cacheObjectWrapper == null)
			return false;

		if (!cacheObjectWrapper.context.equals(context)){
			contextChanged.incrementAndGet();
			return false;
		}
			
		return true;
	}
	
	private Object populateBean(Command<?> command, Context context, BeanPopulator<?> populate, boolean forcedPopulate) throws FunctionalAPIActionFailedException, FunctionalAPIInternalError {

		int mod   		= command.hashCode() % (LOCKS/2);
		int shift 		= LOCKS/2;
		
		// fix the result since |D| is always odd in java  
		int negative	= mod<0?-1:0;
		
		 // shift the result since MOD result could be negative in java  Domain:[-LOCKS/2+1, ..., 0, ..., LOCKS/2-1]
		int index = mod + shift + negative;

		/*
		 * NOTE: only commands with the same hashCode will be blocked while
		 * there is still populateBean in progress(the hashCode value represents
		 * the actual command data members value)
		 */
		synchronized (syncLocks[index]) {

			// Second check 
			Object value = NOT_POPULATED;
			
			if (!forcedPopulate) {
				value = getBeanFromCache(command, context);
			}
			
			if (value == NOT_POPULATED) {
				try {
					markCachableBeanNotFound(command);
					value = populate.call();
					put(command, context, value, getPriority(command));
				} catch (Exception e) {
					logger.error("Bean populator failed !", e);
					if (e instanceof FunctionalAPIActionFailedException){
						throw (FunctionalAPIActionFailedException) e;
					}
					else {
						throw new FunctionalAPIInternalError (FAPIMessages.INTERNAL_ERROR + " " + e.getMessage(), e);
					}
				}
			}
			else{
				markCachebleBeanHasBeenFoundDuringBlock(value);
			}
			return value;
		}
	}

	

	//Helper Methods
	private Priorities getPriority(Command<?> command) {
		Priorities priorityLevel = bindings.getClassPriority(command.getClass());
		return priorityLevel;
	}

	private void put(Command<?> command, Context context,Object value, Priorities priorityLevel) {
		cache.put(command, new BeanWrapper(value, context), priorityLevel);
	}

	private void marksBeanFoundInCache(Object val) {
		logger.debug("Cache: Bean found in cache " 								+ "[BeanType:"+ val.getClass() +"]");
		hitCount.incrementAndGet();
	}

	private void markCachebleBeanHasBeenFoundDuringBlock(Object val) {
		logger.debug("Cache: value has been found in cache during blocking..."  + "[BeanType:"+ val.getClass() +"]");
	}

	private void markCachableBeanNotFound(Command<?> command) {
		logger.debug("Cache: value wasn't found, calling bean populator " + "[KeyType:"+command.getClass()+"]");
	}
	
	private void printCahceCapacities() {
		logger.debug("Cache: BeanFactory Created - LRU Cache Capacities: "+ Arrays.toString(CACHE_CAPACITIES));
	}
	
	enum Priorities {
		LEVEL_1, 
		LEVEL_2, 
		LEVEL_3,
	}

	/* package-private */
	final static class BeanWrapper {
		final private Object value;
		final private Context context;

		BeanWrapper(Object value, Context context) {
			this.value = value;
			this.context = context;
		}

	}

	
	/////////////////////////////
	//Debug Only(Package-private)
	/////////////////////////////
	final private AtomicInteger hitCount    	= new AtomicInteger(0);
	final private AtomicInteger accessCount 	= new AtomicInteger(0);
	final private AtomicInteger nullContext 	= new AtomicInteger(0);
	final private AtomicInteger contextChanged	= new AtomicInteger(0);

	private void printCahceStatistics() {
		logger.debug("***********CahceStatistics************");
		logger.debug("Cache: size:"        	  + cache.size());
		logger.debug("Cache: accessCount:" 	  + accessCount.get());
		logger.debug("Cache: hitCount:"   	  + hitCount.get());
		logger.debug("Cache: hitRatio:"  	  + (double)hitCount.get()/accessCount.get());
		logger.debug("Cache: contextChanged:" + contextChanged.get() + " [Invalidate Bean, context has been changed]");
		logger.debug("Cache: nullContext:"    + nullContext.get());
		logger.debug("**************************************");
	}
	
	/**
	 * BeanFactoryHolder is loaded on the first execution of
	 * Singleton.getInstance() or the first access to SingletonHolder.INSTANCE,
	 * not before.
	 */
	private static class BeanFactoryHolder {
		static final BeanFactory INSTANCE = new BeanFactory();
	}

	public static BeanFactory getInstance() {
		return BeanFactoryHolder.INSTANCE;
	}
	
}
