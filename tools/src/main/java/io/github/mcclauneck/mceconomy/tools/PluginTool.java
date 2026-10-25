package io.github.mcclauneck.mceconomy.tools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Local mirror of MCAgents' {@code io.github.mcpaimon.mcagents.tools.AITool}
 * contract.
 *
 * <p>The only difference is the caller: MCAgents passes its {@code AIAccount}
 * record, which this build cannot name, so {@link MCAgentsToolAdapter} turns it
 * into a {@link ToolCaller} before {@link #execute} runs.</p>
 */
public interface PluginTool {

    /**
     * @return The unique snake_case name the AI calls.
     */
    String getName();

    /**
     * @return What the tool does, written for the AI model.
     */
    String getDescription();

    /**
     * @return A valid JSON schema string describing the arguments.
     */
    String getParametersJsonSchema();

    /**
     * @return The MCAgents category ids this tool belongs to.
     */
    List<String> getCategories();

    /**
     * Runs the tool. Implementations never throw: every failure completes the
     * future with a message beginning {@code Error:}.
     *
     * @param arguments The arguments parsed from the AI's function call; never {@code null}.
     * @param caller    The account that asked for the call.
     * @return The text sent back to the AI.
     */
    CompletableFuture<String> execute(Map<String, Object> arguments, ToolCaller caller);
}
