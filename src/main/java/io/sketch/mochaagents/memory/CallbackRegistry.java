package io.sketch.mochaagents.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 解耦的步骤回调注册中心，对齐 smolagents {@code CallbackRegistry}。
 *
 * <p>支持按 MemoryStep 具体类型注册回调，步骤变更时自动触发。</p>
 */
public final class CallbackRegistry {

    private final List<Registration> registrations = new CopyOnWriteArrayList<>();

    /**
     * 注册某个步骤类型的回调。
     *
     * @param stepClass 步骤类型（如 ActionStep.class）
     * @param callback  回调函数，接收该类型的 MemoryStep
     * @param <T>       步骤具体类型
     */
    public <T extends MemoryStep> void register(Class<T> stepClass, Consumer<T> callback) {
        Objects.requireNonNull(stepClass);
        Objects.requireNonNull(callback);
        registrations.add(new Registration(stepClass, callback));
    }

    /**
     * 触发所有匹配的回调。
     *
     * @param step 当前步骤
     */
    @SuppressWarnings("unchecked")
    public void fire(MemoryStep step) {
        Objects.requireNonNull(step);
        for (Registration reg : registrations) {
            if (reg.stepClass.isAssignableFrom(step.getClass())) {
                ((Consumer<MemoryStep>) reg.callback).accept(step);
            }
        }
    }

    /**
     * 触发所有匹配的回调，并传入额外上下文参数。
     *
     * @param step      当前步骤
     * @param context   附加上下文（如 AgentMemory 引用）
     */
    @SuppressWarnings("unchecked")
    public void fireWithContext(MemoryStep step, Object context) {
        Objects.requireNonNull(step);
        for (Registration reg : registrations) {
            if (reg.stepClass.isAssignableFrom(step.getClass())) {
                ((Consumer<MemoryStep>) reg.callback).accept(step);
            }
        }
    }

    /** 清空所有注册回调。 */
    public void clear() {
        registrations.clear();
    }

    /** 当前注册数量。 */
    public int size() {
        return registrations.size();
    }

    private record Registration(Class<? extends MemoryStep> stepClass, Consumer<? extends MemoryStep> callback) {}
}
