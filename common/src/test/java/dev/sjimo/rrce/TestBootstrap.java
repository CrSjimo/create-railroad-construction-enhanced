package dev.sjimo.rrce;

import net.minecraft.server.Bootstrap;

/** Plain JUnit does not run Forge's event-class transformer. */
public final class TestBootstrap {
    private static boolean prepared;
    public static synchronized void bootStrap() {
        if (prepared) return;
        if (!prepared && "forge".equals(System.getProperty("rrce.test.loader"))) {
            try {
                var helper = Class.forName("net.minecraftforge.eventbus.api.EventListenerHelper")
                    .getDeclaredMethod("getListenerListInternal", Class.class, boolean.class);
                helper.setAccessible(true);
                prepare(Class.forName("net.minecraftforge.network.NetworkEvent", false, TestBootstrap.class.getClassLoader()), helper);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not prepare Forge JUnit bootstrap", error); }
        }
        Bootstrap.bootStrap();
        // Forge normally initializes block-state collision/solidity caches when
        // baking registries, which plain JUnit never runs.
        if ("forge".equals(System.getProperty("rrce.test.loader")))
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK)
                for (var state : block.getStateDefinition().getPossibleStates()) state.initCache();
        prepared = true;
    }
    private static void prepare(Class<?> event, java.lang.reflect.Method helper) throws ReflectiveOperationException {
        if (Class.forName("net.minecraftforge.eventbus.api.Event").isAssignableFrom(event)) prepareHierarchy(event, helper);
        for (Class<?> nested : event.getDeclaredClasses()) prepare(nested, helper);
    }
    private static void prepareHierarchy(Class<?> event, java.lang.reflect.Method helper) throws ReflectiveOperationException {
        if (event.getSuperclass() != Object.class) prepareHierarchy(event.getSuperclass(), helper);
        helper.invoke(null, event, true);
    }
}
