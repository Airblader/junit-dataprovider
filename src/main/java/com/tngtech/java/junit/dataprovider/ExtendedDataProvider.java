package com.tngtech.java.junit.dataprovider;

/**
 * <p>Use this class instead of a method as a DataProvider if you want to,
 * for example, perform certain operations only once before all runs or
 * after all runs have been executed.</p>
 *
 * <p>The only mandatory method to implement is {@code provide}, which will
 * act as the provider just like when using a single method as a data
 * provider.</p>
 *
 * <p>Additionally, the methods {@code beforeAll}, {@code beforeEach},
 * {@code afterAll} and {@code afterEach} can be overwritten to perform
 * certain actions in these events.</p>
 * <p>A typical use-case for this extended provider is setting up a test
 * scenario that all runs will share. Setting it up only once and cleaning
 * it up only once in the end saves overhead.</p>
 */
public abstract class ExtendedDataProvider {

    /**
     * <p>Implement this method to return the parameters the DataProvider should pass to the test.</p>
     * <p><i>Note:</i> This method is used reflectively.
     */
	public abstract Object[][] provide();

	/**
	 * <p>This method will be called before each individual test run.</p>
	 * <p><i>Note:</i> This method is used reflectively.
	 */
	public void beforeEach() {
		/* override this method to use it */
	}

	/**
	 * <p>This method will be called after each individual test run.</p>
	 * <p><i>Note:</i> This method is used reflectively.
	 */
	public void afterEach() {
		/* override this method to use it */
	}

	/**
	 * <p>This method will be called once before all test runs.</p>
	 * <p><i>Note:</i> This method is used reflectively.
	 */
	public void beforeAll() {
		/* override this method to use it */
	}

	/**
	 * <p>This method will be called once after all test runs.</p>
	 * <p><i>Note:</i> This method is used reflectively.
	 */
	public void afterAll() {
		/* override this method to use it */
	}

}