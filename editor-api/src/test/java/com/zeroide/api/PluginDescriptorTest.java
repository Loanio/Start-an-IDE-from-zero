package com.zeroide.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginDescriptorTest {
    @Test
    void nullDependenciesBecomeEmptyList() {
        PluginDescriptor descriptor = new PluginDescriptor("demo", "Demo", "1.0.0", "DemoPlugin", null);

        assertEquals(List.of(), descriptor.dependencies());
    }

    @Test
    void dependenciesAreImmutable() {
        PluginDescriptor descriptor = new PluginDescriptor("demo", "Demo", "1.0.0", "DemoPlugin", List.of("base"));

        assertThrows(UnsupportedOperationException.class, () -> descriptor.dependencies().add("other"));
    }
}
