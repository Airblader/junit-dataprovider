package com.tngtech.java.junit.dataprovider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.util.VisibleForTesting;
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

	@VisibleForTesting
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

	private Statement withRules(FrameworkMethod method, Object target, Statement statement) {
		Statement result = statement;
		for (MethodRule each : getTestClass().getAnnotatedFieldValues(target, Rule.class, MethodRule.class)) {
			result= each.apply(result, method, target);
		}

		return result;
	}

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
			        invokeDataProviderMethod(dataProvider, "beforeAll", errors);
			    }
			    invokeDataProviderMethod(dataProvider, "beforeEach", errors);

			    try {
			        statement.evaluate();
			    } finally {
			        invokeDataProviderMethod(dataProvider, "afterEach", errors);
			        if (computedTestMethods.isLastRunForMethodName(method)) {
			            invokeDataProviderMethod(dataProvider, "afterAll", errors);
			        }
			    }

				MultipleFailureException.assertEmpty(errors);
			}
		};
	}

	/**
	 * Invokes a method on a given dataProvider object and returns its output
	 */
	private Object invokeDataProviderMethod(FrameworkField dataProvider, String methodName, List<Throwable> errors) {
		if (dataProvider == null) {
			return null;
		}

		try {
			Class<?> clazz = Class.forName(dataProvider.getField().getType().getName());
			return clazz.getMethod(methodName, new Class<?>[] {}).invoke(dataProvider.get(clazz));
		} catch (Throwable t) {
			errors.add(t);
			return null;
		}
	}

	/**
     * Validates test methods and their data providers. This method cannot use the result of
     * {@link DataProviderRunner#computeTestMethods()} because the method ignores invalid test methods and data
     * providers silently (except if a data provider method cannot be called). However, the common errors are not raised
     * as {@link RuntimeException} to go the JUnit way of detecting errors. This implies that we have to browse the
     * whole class for test methods and data providers again :-(.
     *
     * @param errors that are added to this list
     * @throws IllegalArgumentException if given {@code errors} is {@code null}
     */
    @VisibleForTesting
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
            } else if (dataProviderMethod != null && !isValidDataProviderMethod(dataProviderMethod)) {
                errors.add(new Error("The data provider method '" + dataProviderName + "' is not valid. "
                        + "A valid method must be public, static, has no arguments parameters and returns 'Object[][]'"));
            } else if (dataProviderField != null && !isValidDataProviderField(dataProviderField)) {
            	errors.add(new Error("The extended data provider '" + dataProviderName + "' is not valid. "));
            }
        }
    }

    /**
     * Generates the exploded list of test methods for the given {@code testMethods}. Each of the given
     * {@link FrameworkMethod}s is checked if it uses a {@code @}{@link DataProvider} or not. If yes, for each line of
     * the {@link DataProvider}s {@link Object}{@code [][]} result a specific test method with its parameters (=
     * {@link Object}{@code []} will be added. If no, the original test method is added.
     *
     * @param testMethods the original test methods
     * @return the exploded list of test methods (never {@code null})
     */
    @VisibleForTesting
    List<FrameworkMethod> generateExplodedTestMethodsFor(List<FrameworkMethod> testMethods) {
        List<FrameworkMethod> result = new ArrayList<FrameworkMethod>();
        if (testMethods == null) {
            return result;
        }

        for (FrameworkMethod testMethod : testMethods) {
            FrameworkMethod dataProviderMethod = getDataProviderMethod(testMethod);
            FrameworkField dataProviderField = getDataProviderField(testMethod);

            if (isValidDataProviderMethod(dataProviderMethod)) {
                result.addAll(explodeTestMethod(testMethod, dataProviderMethod));
            } else if (isValidDataProviderField(dataProviderField)) {
            	result.addAll(explodeTestMethod(testMethod, dataProviderField));
            } else {
                result.add(testMethod);
            }
        }

        return result;
    }

    /**
     * Returns the data provider method that belongs to the given test method or {@code null} if no such data provider
     * exists or the test method is not marked for usage of a data provider
     *
     * @param testMethod test method that uses a data provider
     * @return the data provider or {@code null} (if data provider does not exist or test method does not use any)
     * @throws IllegalArgumentException if given {@code testMethod} is {@code null}
     */
    @VisibleForTesting
    FrameworkMethod getDataProviderMethod(FrameworkMethod testMethod) {
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
     * Returns the extended data provider class that belongs to the given test method or {@code null} if no such
     * data provider exists or the test method is not marked for usage of a data provider.
     *
     * @param testMethod test method that uses a data provider
     * @return the data provider or {@code null}
     * @throws IllegalArgumentException if given {@code testMethod} is {@code null}
     */
    private FrameworkField getDataProviderField(FrameworkMethod testMethod) {
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

    @VisibleForTesting
    TestClass findDataProviderLocation(UseDataProvider useDataProvider) {
    	if (useDataProvider.location() == null || useDataProvider.location().length == 0) {
            return getTestClassInt();
        }

        return new TestClass(useDataProvider.location()[0]);
    }

    /**
     * Checks if the given method is a valid data provider. A method is a valid data provider if and only if the method
     * <ul>
     * <li>is not null,</li>
     * <li>is public,</li>
     * <li>is static,</li>
     * <li>has no parameters, and</li>
     * <li>returns an {@link Object}{@code [][]}.</li>
     * </ul>
     *
     * @param dataProviderMethod the method to check
     * @return true if the method is a valid data provider, false otherwise
     */
    @VisibleForTesting
    boolean isValidDataProviderMethod(FrameworkMethod dataProviderMethod) {
    	// @formatter:off
		return dataProviderMethod != null
                && Modifier.isPublic(dataProviderMethod.getMethod().getModifiers())
                && Modifier.isStatic(dataProviderMethod.getMethod().getModifiers())
                && dataProviderMethod.getMethod().getParameterTypes().length == 0
                && dataProviderMethod.getMethod().getReturnType().equals(Object[][].class);
        // @formatter:on
    }

    @VisibleForTesting
    boolean isValidDataProviderField(FrameworkField dataProviderField) {
    	if (dataProviderField == null
    			|| !Modifier.isPublic(dataProviderField.getField().getModifiers())
    			|| !Modifier.isStatic(dataProviderField.getField().getModifiers())) {
    		return false;
    	}

    	Method provide = null;
		try {
			provide = Class.forName(dataProviderField.getField().getType().getName())
					.getMethod("provide", new Class<?>[] {});
		} catch (Throwable e) {
			return false;
		}

		if (provide == null || provide.getParameterTypes().length != 0
				|| !provide.getReturnType().equals(Object[][].class)) {
			return false;
		}

    	return true;
    }

    /**
     * Creates a list of test methods out of an existing test method and its data provider method.
     *
     * @param testMethod the original test method
     * @param dataProviderMethod the data provider method that gives the parameters
     * @return a list of methods, each method bound to a parameter combination returned by the data provider
     */
    @VisibleForTesting
    List<FrameworkMethod> explodeTestMethod(FrameworkMethod testMethod, final FrameworkMethod dataProviderMethod) {
        int index = 1;
        List<FrameworkMethod> result = new ArrayList<FrameworkMethod>();

        Object[][] dataProviderMethodResult;
        try {
            dataProviderMethodResult = (Object[][]) dataProviderMethod.invokeExplosively(null);
        } catch (Throwable t) {
            throw new Error(String.format("Exception while exploding test method using data provider '%s': %s",
                    dataProviderMethod.getName(), t.getMessage()), t);
        }
        if (dataProviderMethodResult == null) {
            throw new IllegalStateException(String.format("Data provider method '%s' must not return 'null'.",
                    dataProviderMethod.getName()));
        }
        if (dataProviderMethodResult.length == 0) {
            throw new IllegalStateException(String.format("Data provider '%s' must not return an empty object array.",
                    dataProviderMethod.getName()));
        }

        for (Object[] parameters : dataProviderMethodResult) {
            result.add(new DataProviderFrameworkMethod(testMethod.getMethod(), index++, dataProviderMethodResult.length-1,
            		parameters));
        }

        return result;
    }

    @VisibleForTesting
    List<FrameworkMethod> explodeTestMethod(FrameworkMethod testMethod, FrameworkField dataProviderField) {
        int index = 1;
        List<FrameworkMethod> result = new ArrayList<FrameworkMethod>();

        Object[][] dataProviderMethodResult;
        try {
        	Class<?> clazz = Class.forName(dataProviderField.getField().getType().getName());
        	Method method = clazz.getMethod("provide", new Class<?>[] {});
            dataProviderMethodResult = (Object[][]) method.invoke(dataProviderField.get(clazz));
        } catch (Throwable t) {
            throw new Error(String.format("Exception while exploding test method using data provider '%s': %s",
                    dataProviderField.getField().getName(), t.getMessage()), t);
        }
        if (dataProviderMethodResult == null) {
            throw new IllegalStateException(String.format("Data provider method '%s' must not return 'null'.",
                    dataProviderField.getField().getName()));
        }
        if (dataProviderMethodResult.length == 0) {
            throw new IllegalStateException(String.format("Data provider '%s' must not return an empty object array.",
                    dataProviderField.getField().getName()));
        }

		for (int i = 0; i < dataProviderMethodResult.length; i++) {
			Object[] parameters = dataProviderMethodResult[i];

            result.add(new DataProviderFrameworkMethod(testMethod.getMethod(), index++,
            		dataProviderMethodResult.length, parameters));
        }

        return result;
    }

    /**
     * Returns a {@link TestClass} object wrapping the class to be executed. This method is required for testing because
     * {@link #getTestClass()} is final and therefore cannot be stubbed :(
     */
    @VisibleForTesting
    protected TestClass getTestClassInt() {
        return getTestClass();
    }
}
