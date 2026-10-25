package io.github.mcclauneck.mceconomy.tools;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Exposes a {@link PluginTool} to MCAgents as an instance of MCAgents' own
 * {@code AITool} interface, without compiling against MCAgents.
 *
 * <p>The proxy is built from the interface as loaded by the MCAgents plugin's
 * classloader, so MCAgents sees an ordinary {@code AITool}. Its
 * {@code AIAccount} argument is read reflectively through the record accessors
 * {@code accountType()} and {@code accountUuid()}; when they cannot be read the
 * caller carries {@code null}s and every tool refuses it.</p>
 */
public final class MCAgentsToolAdapter {

    /**
     * The fully qualified name of MCAgents' tool interface.
     */
    public static final String AI_TOOL_CLASS = "io.github.mcpaimon.mcagents.tools.AITool";

    private MCAgentsToolAdapter() {
    }

    /**
     * Builds an {@code AITool} proxy for the tool, loading the interface from
     * the given classloader.
     *
     * @param mcagentsLoader The MCAgents plugin's classloader.
     * @param tool           The tool to expose.
     * @return An object implementing {@code io.github.mcpaimon.mcagents.tools.AITool}.
     * @throws ClassNotFoundException When the loader does not provide the interface.
     */
    public static Object createProxy(ClassLoader mcagentsLoader, PluginTool tool) throws ClassNotFoundException {
        return createProxy(Class.forName(AI_TOOL_CLASS, false, mcagentsLoader), tool);
    }

    /**
     * Builds a proxy of an already loaded tool interface.
     *
     * @param aiToolInterface The {@code AITool} interface, or any interface with the same shape.
     * @param tool            The tool to expose.
     * @return An object implementing {@code aiToolInterface}.
     */
    public static Object createProxy(Class<?> aiToolInterface, PluginTool tool) {
        if (!aiToolInterface.isInterface()) {
            throw new IllegalArgumentException(aiToolInterface.getName() + " is not an interface");
        }
        return Proxy.newProxyInstance(aiToolInterface.getClassLoader(), new Class<?>[]{aiToolInterface},
                new Handler(tool));
    }

    /**
     * Reads the caller off an MCAgents {@code AIAccount}, failing closed.
     *
     * @param account The account object MCAgents passed, or {@code null}.
     * @return The caller; both fields are {@code null} when nothing could be read.
     */
    public static ToolCaller toCaller(Object account) {
        if (account == null) {
            return new ToolCaller(null, null);
        }
        return new ToolCaller(readString(account, "accountType"), readString(account, "accountUuid"));
    }

    private static String readString(Object target, String accessor) {
        try {
            Object value = target.getClass().getMethod(accessor).invoke(target);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Routes each {@code AITool} call to the wrapped tool.
     */
    private static final class Handler implements InvocationHandler {

        private final PluginTool tool;

        Handler(PluginTool tool) {
            this.tool = tool;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            int arity = args == null ? 0 : args.length;
            switch (method.getName()) {
                case "getName":
                    if (arity == 0) return this.tool.getName();
                    break;
                case "getDescription":
                    if (arity == 0) return this.tool.getDescription();
                    break;
                case "getParametersJsonSchema":
                    if (arity == 0) return this.tool.getParametersJsonSchema();
                    break;
                case "getCategories":
                    if (arity == 0) return this.tool.getCategories();
                    break;
                case "execute":
                    if (arity == 2) {
                        Map<String, Object> arguments = args[0] instanceof Map<?, ?> map
                                ? (Map<String, Object>) map
                                : Map.of();
                        try {
                            return this.tool.execute(arguments, toCaller(args[1]));
                        } catch (RuntimeException failure) {
                            return CompletableFuture.completedFuture(
                                    "Error: The tool " + this.tool.getName() + " failed to run.");
                        }
                    }
                    break;
                case "equals":
                    if (arity == 1) return proxy == args[0];
                    break;
                case "hashCode":
                    if (arity == 0) return System.identityHashCode(proxy);
                    break;
                case "toString":
                    if (arity == 0) return "MCEconomyTool[" + this.tool.getName() + "]";
                    break;
                default:
                    break;
            }
            throw new UnsupportedOperationException("MCEconomy tool proxy does not implement " + method);
        }
    }
}
