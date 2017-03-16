/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.debug;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.impl.DebuggerInstrument;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * Represents debugging related state of a {@link PolyglotEngine}.
 * <p>
 * Access to the (singleton) instance in an engine, is available via:
 * <ul>
 * <li>{@link Debugger#find(PolyglotEngine)}</li>
 * <li>{@link Debugger#find(TruffleLanguage.Env)}</li>
 * <li>{@link DebuggerSession#getDebugger()}</li>
 * </ul>
 *
 * To start new debugger session use {@link #startSession(SuspendedCallback)}. Please see
 * {@link DebuggerSession} for a usage example.
 * <p>
 * The debugger supports diagnostic tracing that can be enabled using the
 * <code>-Dtruffle.debug.trace=true</code> Java property. The output of this tracing is not
 * guaranteed and will change without notice.
 *
 * @see Debugger#startSession(SuspendedCallback)
 * @see DebuggerSession
 * @see Breakpoint
 *
 * @since 0.9
 */
public final class Debugger {

    static final boolean TRACE = Boolean.getBoolean("truffle.debug.trace");

    /*
     * The engine with this debugger was created.
     */
    private final PolyglotEngine sourceVM;
    private final Env env;

    Debugger(PolyglotEngine sourceVM, Env env) {
        this.env = env;
        this.sourceVM = sourceVM;
    }

    /**
     * Starts a new {@link DebuggerSession session} provided with a callback that gets notified
     * whenever the execution is suspended.
     *
     * @param callback the callback to notify
     * @see DebuggerSession
     * @see SuspendedEvent
     * @since 0.17
     */
    public DebuggerSession startSession(SuspendedCallback callback) {
        return new DebuggerSession(this, callback);
    }

    /**
     * Returns a list of all loaded sources. The sources are returned in the order as they have been
     * loaded by the languages.
     *
     * @return an unmodifiable list of sources
     * @since 0.17
     */
    public List<Source> getLoadedSources() {
        final List<Source> sources = new ArrayList<>();
        EventBinding<?> binding = env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.ANY, new LoadSourceListener() {
            public void onLoad(LoadSourceEvent event) {
                sources.add(event.getSource());
            }
        }, true);
        binding.dispose();
        return Collections.unmodifiableList(sources);
    }

    PolyglotEngine getSourceVM() {
        return sourceVM;
    }

    Env getEnv() {
        return env;
    }

    Instrumenter getInstrumenter() {
        return env.getInstrumenter();
    }

    static void trace(String message, Object... parameters) {
        if (TRACE) {
            PrintStream out = System.out;
            out.println("Debugger: " + String.format(message, parameters));
        }
    }

    /**
     * Finds debugger associated with given engine. There is at most one debugger associated with
     * any {@link PolyglotEngine}.
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated debugger, never <code>null</code>
     * @since 0.9
     */
    public static Debugger find(PolyglotEngine engine) {
        return DebuggerInstrument.getDebugger(engine, new DebuggerInstrument.DebuggerFactory() {
            public Debugger create(PolyglotEngine e, Env env) {
                return new Debugger(e, env);
            }
        });
    }

    /**
     * Finds the debugger associated with a given language environment. There is at most one
     * debugger associated with any {@link PolyglotEngine}. Please note that a debugger instance
     * looked up with a language also has access to all other languages and sources that were loaded
     * by them.
     *
     * @param env the language environment to find debugger for
     * @return an instance of associated debugger, never <code>null</code>
     * @since 0.17
     */
    public static Debugger find(TruffleLanguage.Env env) {
        return find((PolyglotEngine) ACCESSOR.findVM(env));
    }

    static final class AccessorDebug extends Accessor {

        /*
         * TODO get rid of this access and replace it with an API in {@link TruffleInstrument.Env}.
         * I don't think {@link CallTarget} is the right return type here as we want to make it
         * embeddable into the current AST.
         */
        @SuppressWarnings("rawtypes")
        protected CallTarget parse(Source code, Node context, String... argumentNames) {
            RootNode rootNode = context.getRootNode();
            Class<? extends TruffleLanguage> languageClass = nodes().findLanguage(rootNode);
            if (languageClass == null) {
                throw new IllegalStateException("Could not resolve language class for root node " + rootNode);
            }
            final TruffleLanguage<?> truffleLanguage = engineSupport().findLanguageImpl(null, languageClass, code.getMimeType());
            return languageSupport().parse(truffleLanguage, code, context, argumentNames);
        }

        /*
         * TODO we should have a way to identify a language in the instrumentation API without
         * accessor.
         */
        @SuppressWarnings("rawtypes")
        protected Class<? extends TruffleLanguage> findLanguage(RootNode rootNode) {
            return nodes().findLanguage(rootNode);
        }

        /*
         * TODO we should have a better way to publish services from instruments to languages.
         */
        protected Object findVM(com.oracle.truffle.api.TruffleLanguage.Env env) {
            return languageSupport().getVM(env);
        }

        /*
         * TODO I initially moved this to TruffleInstrument.Env but decided against as a new API for
         * inline parsing might replace it.
         */
        protected Object evalInContext(Object sourceVM, Node node, MaterializedFrame frame, String code) {
            return languageSupport().evalInContext(sourceVM, code, node, frame);
        }

    }

    static final AccessorDebug ACCESSOR = new AccessorDebug();

}