// Copyright 2021 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.language.pure.dsl.service.execution;

import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.stores.StoreExecutor;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.ExecutionPlan;
import org.finos.legend.engine.shared.core.url.StreamProvider;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractServicePlanExecutor
{
    private static final boolean DEFAULT_ALLOW_JAVA_COMPILATION = false;

    private final String servicePath;
    private final ExecutionPlan plan;
    private final PlanExecutor executor;

    protected AbstractServicePlanExecutor(String servicePath, ExecutionPlan plan, PlanExecutor executor)
    {
        this.servicePath = servicePath;
        this.plan = plan;
        this.executor = executor;
    }

    protected AbstractServicePlanExecutor(String servicePath, String executionPlanResource, PlanExecutor executor)
    {
        this.servicePath = servicePath;
        this.plan = readPlanFromResource(getClass().getClassLoader(), executionPlanResource);
        this.executor = executor;
    }

    protected AbstractServicePlanExecutor(String servicePath, ExecutionPlan plan, boolean allowJavaCompilation)
    {
        this(servicePath, plan, PlanExecutor.newPlanExecutorWithAvailableStoreExecutors(allowJavaCompilation));
    }

    protected AbstractServicePlanExecutor(String servicePath, String executionPlanResource, boolean allowJavaCompilation)
    {
        this(servicePath, executionPlanResource, PlanExecutor.newPlanExecutorWithAvailableStoreExecutors(allowJavaCompilation));
    }

    protected AbstractServicePlanExecutor(String servicePath, ExecutionPlan plan, boolean allowJavaCompilation, StoreExecutor... storeExecutors)
    {
        this(servicePath, plan, PlanExecutor.newPlanExecutor(allowJavaCompilation, storeExecutors));
    }

    protected AbstractServicePlanExecutor(String servicePath, String executionPlanResource, boolean allowJavaCompilation, StoreExecutor... storeExecutors)
    {
        this(servicePath, executionPlanResource, PlanExecutor.newPlanExecutor(allowJavaCompilation, storeExecutors));
    }

    protected AbstractServicePlanExecutor(String servicePath, ExecutionPlan plan)
    {
        this(servicePath, plan, DEFAULT_ALLOW_JAVA_COMPILATION);
    }

    protected AbstractServicePlanExecutor(String servicePath, String executionPlanResource)
    {
        this(servicePath, executionPlanResource, DEFAULT_ALLOW_JAVA_COMPILATION);
    }

    protected AbstractServicePlanExecutor(String servicePath, ExecutionPlan plan, StoreExecutor... storeExecutors)
    {
        this(servicePath, plan, DEFAULT_ALLOW_JAVA_COMPILATION, storeExecutors);
    }

    protected AbstractServicePlanExecutor(String servicePath, String executionPlanResource, StoreExecutor... storeExecutors)
    {
        this(servicePath, executionPlanResource, DEFAULT_ALLOW_JAVA_COMPILATION, storeExecutors);
    }

    public String getServicePath()
    {
        return this.servicePath;
    }

    @Override
    public String toString()
    {
        return "<" + getClass().getSimpleName() + " " + getServicePath() + ">";
    }

    protected ExecutionBuilder newExecutionBuilder(int parameterCount)
    {
        switch (parameterCount)
        {
            case 0:
            {
                return newNoParameterExecutionBuilder();
            }
            case 1:
            {
                return newSingleParameterExecutionBuilder();
            }
            default:
            {
                return new MultiParameterExecutionBuilder(parameterCount);
            }
        }
    }

    protected ExecutionBuilder newNoParameterExecutionBuilder()
    {
        return new NoParameterExecutionBuilder();
    }

    protected ExecutionBuilder newSingleParameterExecutionBuilder()
    {
        return new SingleParameterExecutionBuilder();
    }

    protected ExecutionBuilder newSingleParameterExecutionBuilder(String parameterName, Object parameterValue)
    {
        return newSingleParameterExecutionBuilder().withParameter(parameterName, parameterValue);
    }

    protected ExecutionBuilder newExecutionBuilder()
    {
        return new MultiParameterExecutionBuilder();
    }

    protected Result execute(Map<String, ?> parameters, StreamProvider streamProvider)
    {
        return this.executor.execute(this.plan, parameters, streamProvider);
    }

    protected abstract class ExecutionBuilder
    {
        private StreamProvider streamProvider;

        public ExecutionBuilder withStreamProvider(StreamProvider streamProvider)
        {
            this.streamProvider = streamProvider;
            return this;
        }

        public ExecutionBuilder withParameter(String name, Object value)
        {
            addParameter(name, value);
            return this;
        }

        public Result execute()
        {
            return AbstractServicePlanExecutor.this.execute(getParameters(), this.streamProvider);
        }

        protected abstract void addParameter(String name, Object value);

        protected abstract Map<String, Object> getParameters();
    }

    private class NoParameterExecutionBuilder extends ExecutionBuilder
    {
        @Override
        protected void addParameter(String name, Object value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> getParameters()
        {
            return Collections.emptyMap();
        }
    }

    private class SingleParameterExecutionBuilder extends ExecutionBuilder
    {
        private Map<String, Object> parameters;

        @Override
        protected void addParameter(String name, Object value)
        {
            if (this.parameters != null)
            {
                throw new IllegalStateException("Single parameter already exists");
            }
            this.parameters = Collections.singletonMap(name, value);
        }

        @Override
        protected Map<String, Object> getParameters()
        {
            return this.parameters;
        }
    }

    private class MultiParameterExecutionBuilder extends ExecutionBuilder
    {
        private final Map<String, Object> parameters;

        protected MultiParameterExecutionBuilder(int parameterCount)
        {
            this.parameters = new HashMap<>(parameterCount);
        }

        protected MultiParameterExecutionBuilder()
        {
            this.parameters = new HashMap<>();
        }

        @Override
        protected void addParameter(String name, Object value)
        {
            this.parameters.put(name, value);
        }

        @Override
        protected Map<String, Object> getParameters()
        {
            return this.parameters;
        }
    }

    public static StreamProvider newStreamProvider(InputStream inputStream)
    {
        return newStreamProvider(StreamProviderBuilder.DEFAULT_ID, inputStream);
    }

    public static StreamProvider newStreamProvider(byte[] input)
    {
        return newStreamProvider(StreamProviderBuilder.DEFAULT_ID, input);
    }

    public static StreamProvider newStreamProvider(String input)
    {
        return newStreamProvider(StreamProviderBuilder.DEFAULT_ID, input);
    }

    public static StreamProvider newStreamProvider(String id, InputStream inputStream)
    {
        Objects.requireNonNull(id, "Stream id may not be null");
        Objects.requireNonNull(inputStream, "Stream may not be null");
        return streamId -> checkStream(streamId, id.equals(streamId) ? inputStream : null);
    }

    public static StreamProvider newStreamProvider(String id, byte[] input)
    {
        return newStreamProvider(id, new ByteArrayInputStream(input));
    }

    public static StreamProvider newStreamProvider(String id, String input)
    {
        return newStreamProvider(id, input.getBytes(StandardCharsets.UTF_8));
    }

    public static StreamProvider newStreamProvider(Map<String, ? extends InputStream> streamsById)
    {
        return streamId -> checkStream(streamId, streamsById.get(streamId));
    }

    public static StreamProviderBuilder newStreamProviderBuilder()
    {
        return new StreamProviderBuilder();
    }

    private static InputStream checkStream(String streamId, InputStream stream)
    {
        if (stream == null)
        {
            throw new IllegalArgumentException("Unknown stream: " + streamId);
        }
        return stream;
    }

    public static class StreamProviderBuilder
    {
        private static final String DEFAULT_ID = "default";

        private String firstId = null;
        private InputStream firstStream = null;
        private Map<String, InputStream> inputStreams = null;

        private StreamProviderBuilder()
        {
        }

        public StreamProviderBuilder withInput(InputStream input)
        {
            return withInput(DEFAULT_ID, input);
        }

        public StreamProviderBuilder withInput(byte[] input)
        {
            return withInput(DEFAULT_ID, input);
        }

        public StreamProviderBuilder withInput(String input)
        {
            return withInput(DEFAULT_ID, input);
        }

        public StreamProviderBuilder withInput(String id, InputStream input)
        {
            registerInputStream(id, input);
            return this;
        }

        public StreamProviderBuilder withInput(String id, byte[] input)
        {
            return withInput(id, new ByteArrayInputStream(input));
        }

        public StreamProviderBuilder withInput(String id, String input)
        {
            return withInput(id, input.getBytes(StandardCharsets.UTF_8));
        }

        public StreamProvider build()
        {
            if (this.firstId == null)
            {
                return streamId -> { throw new IllegalArgumentException("Unknown input stream: " + streamId); };
            }
            if (this.inputStreams == null)
            {
                return newStreamProvider(this.firstId, this.firstStream);
            }
            Map<String, InputStream> streams = new HashMap<>(this.inputStreams);
            streams.put(this.firstId, this.firstStream);
            return newStreamProvider(streams);
        }

        private void registerInputStream(String id, InputStream stream)
        {
            if (id == null)
            {
                throw new IllegalArgumentException("id may not be null");
            }
            if (this.firstId == null)
            {
                this.firstId = id;
                this.firstStream = stream;
            }
            else if (this.firstId.equals(id))
            {
                throw new IllegalArgumentException("There is already an InputStream for id: " + id);
            }
            else
            {
                if (this.inputStreams == null)
                {
                    this.inputStreams = new HashMap<>();
                }
                if (this.inputStreams.putIfAbsent(id, stream) != null)
                {
                    throw new IllegalArgumentException("There is already an InputStream for id: " + id);
                }
            }
        }
    }

    protected static ExecutionPlan readPlanFromResource(String planResourceName)
    {
        return readPlanFromResource(null, planResourceName);
    }

    protected static ExecutionPlan readPlanFromResource(ClassLoader classLoader, String planResourceName)
    {
        URL url = ((classLoader == null) ? Thread.currentThread().getContextClassLoader() : classLoader).getResource(planResourceName);
        if (url == null)
        {
            throw new RuntimeException("Could not find execution plan: " + planResourceName);
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)))
        {
            return PlanExecutor.readExecutionPlan(reader);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error loading execution plan from resource: " + planResourceName, e);
        }
    }

    protected static ExecutionPlan readPlanFromFile(Path planFile)
    {
        try (Reader reader = Files.newBufferedReader(planFile, StandardCharsets.UTF_8))
        {
            return PlanExecutor.readExecutionPlan(reader);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error loading execution plan from file: " + planFile);
        }
    }
}
