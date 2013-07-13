package com.tngtech.java.junit.dataprovider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.runners.model.FrameworkMethod;

class DataProviderMethodsList {
    private List<FrameworkMethod> methods = new ArrayList<FrameworkMethod>();
    private Map<String, Counter> counter = new HashMap<String, Counter>();

    public List<FrameworkMethod> getListOfComputedMethods() {
        return methods;
    }

    public void setListOfComputedMethods(List<FrameworkMethod> methods) {
        this.methods = methods;
        updateCounters();
    }

    // TODO move this methods here right away..
    public int getCurrentIndexForMethodName(FrameworkMethod method) {
        return counter.get(getFullName(method)).getCurrentIndex();
    }

    public void increaseCurrentIndexForMethodName(FrameworkMethod method) {
        counter.get(getFullName(method)).increaseCurrentIndex();
    }

    public boolean isLastRunForMethodName(FrameworkMethod method) {
        return counter.get(getFullName(method)).isLastRun();
    }

    private void updateCounters() {
        counter.clear();
        for (FrameworkMethod method : methods) {
            String name = getFullName(method);
            if (!counter.containsKey(name)) {
                counter.put(name, new Counter());
            }

            counter.get(name).increaseNumberOfRuns();
        }
    }

    private String getFullName(FrameworkMethod method) {
        if (method == null || method.getMethod() == null || method.getMethod().getDeclaringClass() == null) {
            return null;
        }

        return method.getMethod().getDeclaringClass().getName() + "." + method.getMethod().getName();
    }

    private static class Counter {
        private int currentIndex = 0;
        private int numberOfRuns = 0;

        public int getCurrentIndex() {
            return currentIndex;
        }

        public void increaseCurrentIndex() {
            currentIndex++;
        }

        public void increaseNumberOfRuns() {
            numberOfRuns++;
        }

        public boolean isLastRun() {
            return numberOfRuns == 1 || currentIndex == numberOfRuns;
        }
    }
}