/*
 * Decompiled with CFR.
 */
package org.benf.cfr.tests;

import org.benf.cfr.tests.support.UnaryFunction;

public class AnonymousInnerClassTest3 {
    Integer invoker(int arg, UnaryFunction<Integer, Integer> fn) {
        return fn.invoke(arg);
    }

    public int doit(final int x) {
        return this.invoker(x, new UnaryFunction<Integer, Integer>(){

            @Override
            public Integer invoke(Integer arg) {
                return arg * new X().hashCode() + x;
            }

            class X {
                X() {
                }
            }
        });
    }
}
