package com.tngtech.java.junit.dataprovider;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class ExtendedDataProviderTest {

	@DataProvider
	public static ExtendedDataProvider dataProvider = new ExtendedDataProvider() {
		@Override
		public Object[][] provide() {
			System.out.println("provide()");

			return new Object[][] {
					{ 1, "One" }, { 2, "Two" }, { 3, "Three" }
			};
		}

		@Override
		public void beforeAll() {
			System.out.println("beforeAll()");
		}

		@Override
		public void beforeEach() {
			System.out.println("beforeEach()");
		}

		@Override
		public void afterAll() {
			System.out.println("afterAll()");
		}

		@Override
		public void afterEach() {
			System.out.println("afterEach()");
		}
	};

	@Test
	@UseDataProvider("dataProvider")
	public void testExtendedDataProvider(int number, String word) {
		System.out.println("testExtendedDataProvider : number = " + number + ", word = " + word);
	}

	@DataProvider
	public static ExtendedDataProvider secondProvider = new ExtendedDataProvider() {
		@Override
		public Object[][] provide() {
			return new Object[][] { { 1 }, { 1 } };
		}
	};

	@Test
	@UseDataProvider("secondProvider")
	public void testSecondExtendedDataProvider(int number) {
		Assert.assertEquals(number, 1);
	}

	@DataProvider
	public static ExtendedDataProvider thirdProvider = new ExtendedDataProvider() {
		@Override
		public Object[][] provide() {
			return new Object[][] { { 1 }, { 1 }, { 1 }, { 1 } };
		}
	};

	@Test
	@UseDataProvider("thirdProvider")
	public void testThirdExtendedDataProvider(int number) {
		Assert.assertEquals(number, 1);
	}

	@Test
	public void testWithoutDataProvider() throws Throwable {
		System.out.println("testWithoutDataProvider");
	}

}
