package com.tngtech.java.junit.dataprovider;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;

/**
 * A special framework method that allows the usage of parameters for the test method.
 */
public class DataProviderFrameworkMethod extends FrameworkMethod {

    /**
     * Index of exploded test method such that each get a unique name.
     */
	// TODO use 1-based index
    private int index = 0;

    /**
     * Number of rows the DataProvider returns.
     */
    private int numberOfRows = 1;

    /**
	  * The {@link ExtendedDataProvider} object. If none was used, it will be {@code null}.
     */
    private FrameworkField extendedDataProvider = null;

    /**
     * Parameters to invoke the test method.
     */
    private final Object[] parameters;

    public DataProviderFrameworkMethod(Method method, int index, Object[] parameters) {
    	this(method, index, 1, parameters);
    }

    public DataProviderFrameworkMethod(Method method, int index, int numberOfRows, Object[] parameters) {
        super(method);

        setNumberOfRows(numberOfRows);
        setIndex(index);

        if (parameters == null) {
            throw new IllegalArgumentException("parameter must not be null");
        }
        if (parameters.length == 0) {
            throw new IllegalArgumentException("parameter must not be empty");
        }

        this.parameters = Arrays.copyOf(parameters, parameters.length);
    }

    public void setExtendedDataProvider (FrameworkField provider) {
    	extendedDataProvider = provider;
    }

    public void setNumberOfRows(int numberOfRows) {
    	this.numberOfRows = numberOfRows;
    }

    public int getNumberOfRows() {
    	return numberOfRows;
    }

    public void setIndex(int index) {
    	this.index = index;
    }

    public int getIndex() {
    	return index;
    }

    public Object[] getParameters() {
    	return parameters;
    }

    @Override
    public String getName() {
        return String.format("%s[%d: %s]", super.getName(), index, format(parameters));
    }

    @Override
    public Object invokeExplosively(Object target, Object... params) throws Throwable {
    	// TODO make sure before/after methods all run even if one of them fails

    	// TODO check index/numberOfRows
    	System.out.println(getMethod().getName() + " : " + getIndex() + "/" + getNumberOfRows());

		if (getNumberOfRows() == 1 || getIndex() == 0) {
			invokeExtendedDataProviderMethod("beforeAll");
		}

		invokeExtendedDataProviderMethod("beforeEach");

    	Object result = null;
    	try {
    		result = super.invokeExplosively(target, parameters);
    	} catch (Throwable t) {
    		throw t;
    	} finally {
    		invokeExtendedDataProviderMethod("afterEach");

    		if (getNumberOfRows() == 1 || getIndex() == getNumberOfRows()) {
    			invokeExtendedDataProviderMethod("afterAll");
    		}
    	}

    	return result;
    }

    private void invokeExtendedDataProviderMethod(String methodName) throws Throwable {
    	if (extendedDataProvider == null) {
    		return;
    	}

		Class<?> clazz = Class.forName(extendedDataProvider.getField().getType().getName());
		clazz.getMethod(methodName, new Class<?>[] {}).invoke(extendedDataProvider.get(clazz));
    }

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + index;
		result = prime * result + numberOfRows;
		result = prime * result + Arrays.hashCode(parameters);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		DataProviderFrameworkMethod other = (DataProviderFrameworkMethod) obj;
		if (index != other.index)
			return false;
		if (numberOfRows != other.numberOfRows)
			return false;
		if (!Arrays.equals(parameters, other.parameters))
			return false;
		return true;
	}

	/**
     * Returns a string representation of the given parameters. The parameters are converted to string by the following
     * rules:
     * <ul>
     * <li>null -&gt; &lt;null&gt;</li>
     * <li>&quot;%quot; (= empty string) -&gt; &lt;empty string&gt;</li>
     * <li>array (e.g. String[]) -&gt; &lt;array&gt;</li>
     * <li>other -&gt; Object.toString</li>
     * </ul>
     *
     * @param parameters the parameters are converted to a comma-separated string
     * @return a string representation of the given parameters
     */
    private <T> String format(T[] parameters) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < parameters.length; i++) {
            Object param = parameters[i];
            if (param == null) {
                stringBuilder.append("<null>");

            } else if (param.getClass().isArray()) {
                if (param.getClass().getComponentType().isPrimitive()) {
                    appendTo(stringBuilder, param);
                } else {
                    stringBuilder.append('[').append(format(getArray(param))).append(']');
                }

            } else if (param instanceof String && ((String) param).isEmpty()) {
                stringBuilder.append("<empty string>");

            } else {
                stringBuilder.append(param.toString());
            }

            if (i < parameters.length - 1) {
                stringBuilder.append(", ");
            }
        }

        return stringBuilder.toString();
    }

    private void appendTo(StringBuilder stringBuilder, Object primitiveArray) {
        Class<?> componentType = primitiveArray.getClass().getComponentType();
        if (boolean.class.equals(componentType)) {
            stringBuilder.append(Arrays.toString((boolean[]) primitiveArray));
        } else if (byte.class.equals(componentType)) {
            stringBuilder.append(Arrays.toString((byte[]) primitiveArray));
        } else if (char.class.equals(componentType)) {
            stringBuilder.append(Arrays.toString((char[]) primitiveArray));
        } else if (short.class.equals(componentType)) {
            stringBuilder.append(Arrays.toString((short[]) primitiveArray));
        } else if (int.class.equals(componentType)) {
            stringBuilder.append(Arrays.toString((int[]) primitiveArray));
        } else if (long.class.equals(componentType)) {
            stringBuilder.append(Arrays.toString((long[]) primitiveArray));
        } else if (float.class.equals(componentType)) {
            stringBuilder.append(Arrays.toString((float[]) primitiveArray));
        } else if (double.class.equals(componentType)) {
            stringBuilder.append(Arrays.toString((double[]) primitiveArray));
        }
    }

    private <T> T[] getArray(Object array) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) array;
        return result;
    }
}
