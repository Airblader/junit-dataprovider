package com.tngtech.java.junit.dataprovider;

/**
 * Use this class instead of a method as a DataProvider if you want to,
 * for example, perform certain operations only once before all runs or
 * after all runs have been executed.
 */
public abstract class ExtendedDataProvider {

	/**
	 * Implement this method to return the parameters the DataProvider should pass to the test.
	 */
	public abstract Object[][] provide();

	/**
	 * This method will be called before each individual test run.
	 */
	public void beforeEach() {
		/* override this method to use it */
	}

	/**
	 * This method will be called after each individual test run.
	 */
	public void afterEach() {
		/* override this method to use it */
	}

	/**
	 * This method will be called once before all test runs.
	 */
	public void beforeAll() {
		/* override this method to use it */
	}

	/**
	 * This method will be called once after all test runs.
	 */
	public void afterAll() {
		/* override this method to use it */
	}

}