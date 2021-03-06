package com.tngtech.java.junit.dataprovider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.runners.model.MultipleFailureException;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.rules.MethodRule;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * A custom runner for JUnit that allows the usage of <a href="http://testng.org/">TestNG</a>-like data providers. Data
 * providers are public, static methods that return an {@link Object}{@code [][]} (see {@link DataProvider}). Alternatively,
 * a {@link ExtendedDataProvider} can be used and marked as a data provider.
 * <p>
 * Your test method must be annotated with {@code @}{@link UseDataProvider}, additionally.
 */
public class DataProviderRunner extends BlockJUnit4ClassRunner {

	/** <p>This field is visible for testing</p> */
	protected DataProviderMethodsList computedTestMethods;

    /**
     * Creates a DataProviderRunner to run supplied {@code clazz}.
     *
     * @param clazz the test {@link Class} to run
     * @throws InitializationError if the test {@link Class} is malformed.
     */
    public DataProviderRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    @Override
    public void filter(final Filter filter) throws NoTestsRemainException {
		DataProviderFilter dataProviderFilter = new DataProviderFilter(filter);
		computedTestMethods.setListOfComputedMethods(getFilteredMethods(dataProviderFilter));

        super.filter(dataProviderFilter);
    }

	/**
	 * Returns a list of all tests that will be run after applying the specified filter.
	 */
	private List<FrameworkMethod> getFilteredMethods(DataProviderFilter dataProviderFilter) {
		List<FrameworkMethod> newList = new ArrayList<FrameworkMethod>();
		for (FrameworkMethod method : computeTestMethods()) {
			if (dataProviderFilter.shouldRun(Description.createTestDescription(method.getMethod().getDeclaringClass(),
					method.getName()))) {
				newList.add(method);
			}
		}

		return newList;
	}

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        if (computedTestMethods == null) {
            computedTestMethods = new DataProviderMethodsList();
            computedTestMethods.setListOfComputedMethods(generateExplodedTestMethodsFor(super.computeTestMethods()));
        }

        return computedTestMethods.getListOfComputedMethods();
    }

    @Override
    protected void collectInitializationErrors(List<Throwable> errors) {
        super.collectInitializationErrors(errors);
        validateDataProviderObjects(errors);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if given {@code errors} is {@code null}
     */
    @Override
    protected void validateTestMethods(List<Throwable> errors) {
        if (errors == null) {
            throw new IllegalArgumentException("errors must not be null");
        }
        for (FrameworkMethod method : getTestClassInt().getAnnotatedMethods(Test.class)) {
            if (method.getAnnotation(UseDataProvider.class) == null) {
                method.validatePublicVoidNoArg(false, errors);
            } else {
                method.validatePublicVoid(false, errors);
            }
        }
    }

    /**
     * <p><i>Note:</i> This method was copied from {@link BlockJUnit4ClassRunner} to add data provider
     * specific handling.</p>
     * {@inheritDoc}
     */
    @Override
	@SuppressWarnings("deprecation")
	protected Statement methodBlock(FrameworkMethod method) {
		Object test;
		try {
			test = new ReflectiveCallable() {
				@Override
				protected Object runReflectiveCall() throws Throwable {
					return createTest();
				}
			}.run();
		} catch (Throwable e) {
			return new Fail(e);
		}

		Statement statement = methodInvoker(method, test);
		statement = possiblyExpectingExceptions(method, test, statement);
		statement = withPotentialTimeout(method, test, statement);
		statement = withDataProviderMethods(method, statement);
		statement = withBefores(method, test, statement);
		statement = withAfters(method, test, statement);
		statement = withRules(method, test, statement);

		return statement;
	}

    /**
     * <p><i>Note:</i> This method was copied from {@link BlockJUnit4ClassRunner} as it is marked
     * {@code private} and could not be referenced otherwise.</p>
     * {@inheritDoc}
     */
	private Statement withRules(FrameworkMethod method, Object target, Statement statement) {
		Statement result = statement;
		for (MethodRule each : getTestClass().getAnnotatedFieldValues(target, Rule.class, MethodRule.class)) {
			result= each.apply(result, method, target);
		}

		return result;
	}

	/**
	 * <p>Adds data provider specific handling to a given statement by executing calls of
	 * {@code beforeAll}, {@code beforeEach}, {@code afterEach} and {@code afterAll} in the
	 * proper order if needed.</p>
	 * <p>If there is no data provider field for the given {@code method}, the statement will
	 * be returned untouched.</p>
	 *
	 * @param method
	 * @param statement
	 * @return The modified statement or just {@code statement} if {@code method} has no valid
	 * data provider field corresponding to it.
	 */
	private Statement withDataProviderMethods(final FrameworkMethod method, final Statement statement) {
		final FrameworkField dataProvider = getDataProviderField(method);
		if (dataProvider == null) {
		    return statement;
		}

		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
			    List<Throwable> errors = new ArrayList<Throwable>();

			    computedTestMethods.increaseCurrentIndexForMethodName(method);
			    if (computedTestMethods.getCurrentIndexForMethodName(method) == 1) {
			        invokeFrameworkFieldMethod(dataProvider, "beforeAll", errors);
			    }
			    invokeFrameworkFieldMethod(dataProvider, "beforeEach", errors);

			    try {
			        statement.evaluate();
			    } finally {
			        invokeFrameworkFieldMethod(dataProvider, "afterEach", errors);
			        if (computedTestMethods.isLastRunForMethodName(method)) {
			            invokeFrameworkFieldMethod(dataProvider, "afterAll", errors);
			        }
			    }

				MultipleFailureException.assertEmpty(errors);
			}
		};
	}

	/**
	 * <p>Invoke a method on a field by its name.</p>
	 * <p><i>Note:</i> This method will modify the passed {@code errors} list if any errors occur.</p>
	 *
	 * @param field The {@link FrameworkField} to use
	 * @param methodName Name of the method that should be called
	 * @param errors A {@link List} of {@link Throwable} that will be modified in case of an exception
	 * @return The value returned by the called method or {@code null} if {@code dataProvider} was
	 * {@code null} or if any errors occurred during execution (in this case, the thrown exception will
	 * be added to {@code errors}
	 */
	private Object invokeFrameworkFieldMethod(FrameworkField field, String methodName, List<Throwable> errors) {
		if (field == null) {
			return null;
		}

		try {
			Class<?> clazz = Class.forName(field.getField().getType().getName());
			return clazz.getMethod(methodName, new Class<?>[] {}).invoke(field.get(clazz));
		} catch (Throwable t) {
			errors.add(t);
			return null;
		}
	}

	/**
     * <p>Validates test methods and their data providers. This method cannot use the result of
     * {@link DataProviderRunner#computeTestMethods()} because the method ignores invalid test methods and data
     * providers silently (except if a data provider method cannot be called). However, the common errors are not raised
     * as {@link RuntimeException} to go the JUnit way of detecting errors. This implies that we have to browse the
     * whole class for test methods and data providers again :-(.</p>
     *
     * <p><i>Note:</i> This method is visible for testing.</p>
     *
     * @param errors that are added to this list
     * @throws IllegalArgumentException if given {@code errors} is {@code null}
     */
    void validateDataProviderObjects(List<Throwable> errors) {
        if (errors == null) {
            throw new IllegalArgumentException("errors must not be null");
        }

        for (FrameworkMethod testMethod : getTestClassInt().getAnnotatedMethods(UseDataProvider.class)) {
            String dataProviderName = testMethod.getAnnotation(UseDataProvider.class).value();

            FrameworkMethod dataProviderMethod = getDataProviderMethod(testMethod);
            FrameworkField dataProviderField = getDataProviderField(testMethod);

            if (dataProviderMethod == null && dataProviderField == null) {
                errors.add(new Error("No such data provider: " + dataProviderName));
            } else if (dataProviderMethod != null && !isValidDataProvider(dataProviderMethod)) {
                errors.add(new Error("The data provider method '" + dataProviderName + "' is not valid. "
                        + "A valid method must be public, static, has no arguments parameters and returns 'Object[][]'"));
            } else if (dataProviderField != null && !isValidDataProvider(dataProviderField)) {
            	errors.add(new Error("The extended data provider '" + dataProviderName + "' is not valid. "));
            }
        }
    }

    /**
     * <p>Generates the exploded list of test methods for the given {@code testMethods}. Each of the given
     * {@link FrameworkMethod}s is checked if it uses a {@code @}{@link DataProvider} or not. If yes, for each line of
     * the {@link DataProvider}s {@link Object}{@code [][]} result a specific test method with its parameters (=
     * {@link Object}{@code []} will be added. If no, the original test method is added.</p>
     *
     * <p><i>Note:</i> This method is visible for testing.</p>
     *
     * @param testMethods the original test methods
     * @return the exploded list of test methods (never {@code null})
     */
    List<FrameworkMethod> generateExplodedTestMethodsFor(List<FrameworkMethod> testMethods) {
        List<FrameworkMethod> result = new ArrayList<FrameworkMethod>();
        if (testMethods == null) {
            return result;
        }

        for (FrameworkMethod testMethod : testMethods) {
            FrameworkMethod dataProviderMethod = getDataProviderMethod(testMethod);
            FrameworkField dataProviderField = getDataProviderField(testMethod);

            if (isValidDataProvider(dataProviderMethod)) {
                result.addAll(explodeTestMethod(testMethod, dataProviderMethod));
            } else if (isValidDataProvider(dataProviderField)) {
            	result.addAll(explodeTestMethod(testMethod, dataProviderField));
            } else {
                result.add(testMethod);
            }
        }

        return result;
    }

    /**
     * <p>Returns the data provider method that belongs to the given test method or {@code null} if no such data provider
     * exists or the test method is not marked for usage of a data provider.</p>
     *
     * <p><i>Note:</i> This method is visible for testing.</p>
     *
     * @param testMethod test method that uses a data provider
     * @return the data provider or {@code null} (if data provider does not exist or test method does not use any)
     * @throws IllegalArgumentException if given {@code testMethod} is {@code null}
     */
    protected FrameworkMethod getDataProviderMethod(FrameworkMethod testMethod) {
        if (testMethod == null) {
            throw new IllegalArgumentException("testMethod must not be null");
        }

        UseDataProvider useDataProvider = testMethod.getAnnotation(UseDataProvider.class);
        if (useDataProvider == null) {
            return null;
        }

        TestClass dataProviderLocation = findDataProviderLocation(useDataProvider);
        for (FrameworkMethod method : dataProviderLocation.getAnnotatedMethods(DataProvider.class)) {
            if (method.getName().equals(useDataProvider.value())) {
                return method;
            }
        }

        return null;
    }

    /**
     * <p>Returns the extended data provider class that belongs to the given test method or {@code null} if no such
     * data provider exists or the test method is not marked for usage of a data provider.</p>
     *
     * <p><i>Note:</i> This method is visible for testing.</p>
     *
     * @param testMethod test method that uses a data provider
     * @return the data provider or {@code null}
     * @throws IllegalArgumentException if given {@code testMethod} is {@code null}
     */
    protected FrameworkField getDataProviderField(FrameworkMethod testMethod) {
    	if (testMethod == null) {
    		throw new IllegalArgumentException("testMethod must not be null");
    	}

    	UseDataProvider useDataProvider = testMethod.getAnnotation(UseDataProvider.class);
    	if (useDataProvider == null) {
    		return null;
    	}

        TestClass dataProviderLocation = findDataProviderLocation(useDataProvider);
        for (FrameworkField field : dataProviderLocation.getAnnotatedFields(DataProvider.class)) {
        	if (field.getField().getName().equals(useDataProvider.value())) {
        		return field;
        	}
        }

    	return null;
    }

    /** <p><i>Note:</i> This method is visible for testing.</p> */
    TestClass findDataProviderLocation(UseDataProvider useDataProvider) {
    	if (useDataProvider.location() == null || useDataProvider.location().length == 0) {
            return getTestClassInt();
        }

        return new TestClass(useDataProvider.location()[0]);
    }

    /**
     * <p>Checks if the given method is a valid data provider.</p>
     * <p>A valid data provider method must meet the following conditions:</p>
     * <ul>
     * <li>The method must be {@code public}</li>
     * <li>The method must be {@code static}</li>
     * <li>The method must not take any parameters</li>
     * <li>The method must return an {@code Object[][]}</li>
     * </ul>
     *
     * <p><i>Note:</i> This method is visible for testing.</p>
     *
     * @param dataProvider the method to check
     * @return true if {@code dataProvider} is a valid data provider, false otherwise or if
     * the argument is {@code null}.
     */
    protected boolean isValidDataProvider(FrameworkMethod dataProviderMethod) {
    	// @formatter:off
		return dataProviderMethod != null
                && Modifier.isPublic(dataProviderMethod.getMethod().getModifiers())
                && Modifier.isStatic(dataProviderMethod.getMethod().getModifiers())
                && dataProviderMethod.getMethod().getParameterTypes().length == 0
                && dataProviderMethod.getMethod().getReturnType().equals(Object[][].class);
        // @formatter:on
    }

    /**
     * Checks if the given field is a valid data provider.
     * <p>A valid data provider field must meet the following conditions:</p>
     * <ul>
     * <li>The field's type must be an instance of {@link ExtendedDataProvider}</li>
     * <li>The field must be public</li>
     * <li>The field must be static</li>
     * </ul>
     *
     * <p><i>Note:</i> This method is visible for testing.</p>
     *
     * @param dataProvider the field to check
     * @return true if {@code dataProvider} is a valid data provider, false otherwise or if
     * the argument is {@code null}.
     */
    protected boolean isValidDataProvider(FrameworkField dataProviderField) {
        // the correctness of the provide method itself is enforced in the data provider class
    	return dataProviderField != null
    	        && dataProviderField.getField().getType() == ExtendedDataProvider.class
    			&& Modifier.isPublic(dataProviderField.getField().getModifiers())
    			&& Modifier.isStatic(dataProviderField.getField().getModifiers());
    }

    /**
     * <p>Creates a list of test methods out of an existing test method and its data provider method.</p>
     * <p>The generic type {@code T} can either be a {@link FrameworkMethod} or {@link FrameworkField},
     * otherwise an {@link IllegalArgumentException} will be thrown.</p>
     *
     * <p><i>Note:</i> This method is visible for testing.</p>
     *
     * @param testMethod the original test method
     * @param dataProvider the data provider method that gives the parameters
     * @return If {@code dataProvider} is an instance of {@link FrameworkMethod} or {@link FrameworkField},
     * a list of methods, each method bound to a parameter combination returned by the data provider, is
     * returned. Otherwise, an {@link IllegalArgumentException} will be thrown.
     */
    protected <T> List<FrameworkMethod> explodeTestMethod(FrameworkMethod testMethod, T dataProvider) {
        Method method = null;
        Object target = null;

        if (dataProvider instanceof FrameworkMethod) {
            method = ((FrameworkMethod) dataProvider).getMethod();
            target = null;
        } else if (dataProvider instanceof FrameworkField) {
            FrameworkField dataProviderField = (FrameworkField) dataProvider;

            try {
                Class<?> clazz = Class.forName(dataProviderField.getField().getType().getName());
                method = clazz.getMethod("provide", new Class<?>[] {});
                target = dataProviderField.get(clazz);
            } catch (Throwable t) {
                throw new Error(String.format("Exception while exploding test method using data provider '%s'",
                        dataProviderField.getField().getName()), t);
            }
        } else {
            throw new IllegalArgumentException("Parameter dataProvider must be a method or field");
        }

        return explodeTestMethod(testMethod, method, target);
    }

    /** <p><i>Note:</i> This method is visible for testing.</p> */
    protected List<FrameworkMethod> explodeTestMethod(FrameworkMethod testMethod, Method dataProvider, Object target) {
        int index = 1;
        List<FrameworkMethod> result = new ArrayList<FrameworkMethod>();

        Object[][] dataProviderMethodResult = null;
        try {
            dataProviderMethodResult = invokeDataProvider(dataProvider, target);
        } catch (Throwable t) {
            throw new Error(String.format("Exception while exploding test method using data provider '%s': %s",
                   (dataProvider != null) ? dataProvider.getName() : "<null>", t.getMessage()), t);
        }

        if (dataProviderMethodResult == null) {
            throw new IllegalStateException(String.format("Data provider method '%s' must not return 'null'.",
                    (dataProvider != null) ? dataProvider.getName() : "<null>"));
        }

        if (dataProviderMethodResult.length == 0) {
            throw new IllegalStateException(String.format("Data provider '%s' must not return an empty object array.",
                    (dataProvider != null) ? dataProvider.getName() : "<null>"));
        }

        for (Object[] parameters : dataProviderMethodResult) {
            result.add(new DataProviderFrameworkMethod(testMethod.getMethod(), index++, dataProviderMethodResult.length,
                    parameters));
        }

        return result;
    }

    /** <p>This is extracted into a method for testing purposes.</p> */
    protected Object[][] invokeDataProvider(Method dataProvider, Object target) throws Throwable {
        return (Object[][]) dataProvider.invoke(target);
    }

    /**
     * <p>Returns a {@link TestClass} object wrapping the class to be executed. This method is required for testing because
     * {@link #getTestClass()} is final and therefore cannot be stubbed :(</p>
     *
     * <p><i>Note:</i> This method is visible for testing.</p>
     */
    protected TestClass getTestClassInt() {
        return getTestClass();
    }
}
