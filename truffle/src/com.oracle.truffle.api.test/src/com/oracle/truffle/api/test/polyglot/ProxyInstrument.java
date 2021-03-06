/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.test.polyglot;

import java.util.function.Consumer;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument.Initialize;

/**
 * Reusable language for testing that allows wrap all methods.
 */
@TruffleInstrument.Registration(id = ProxyInstrument.ID, name = ProxyInstrument.ID, version = "1.0", services = Initialize.class)
public class ProxyInstrument extends TruffleInstrument {

    public static final String ID = "proxyInstrument";

    public interface Initialize {
    }

    private static volatile ProxyInstrument delegate = new ProxyInstrument();
    static {
        delegate.wrapper = false;
    }
    private boolean wrapper = true;
    protected ProxyInstrument languageInstance;
    private Consumer<Env> onCreate;

    public static <T extends ProxyInstrument> T setDelegate(T delegate) {
        ((ProxyInstrument) delegate).wrapper = false;
        ProxyInstrument.delegate = delegate;
        return delegate;
    }

    public void setOnCreate(Consumer<Env> onCreate) {
        this.onCreate = onCreate;
    }

    @Override
    protected void onCreate(Env env) {
        env.registerService(new Initialize() {
        });
        if (wrapper) {
            delegate.languageInstance = this;
            delegate.onCreate(env);
        }
        if (onCreate != null) {
            onCreate.accept(env);
        }
    }

    @Override
    protected void onDispose(Env env) {
        if (wrapper) {
            delegate.languageInstance = this;
            delegate.onDispose(env);
        }
    }

    @Override
    protected void onFinalize(Env env) {
        if (wrapper) {
            delegate.languageInstance = this;
            delegate.onFinalize(env);
        }
    }

}
