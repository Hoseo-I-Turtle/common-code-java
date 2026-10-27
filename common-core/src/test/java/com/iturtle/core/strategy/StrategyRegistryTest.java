package com.iturtle.core.strategy;

import com.iturtle.core.exception.InvalidConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyRegistryTest {

    enum Type { A, B, C }

    record TypeStrategy(Type key) implements Strategy<Type> {}

    @Test
    void get() {
        TypeStrategy a = new TypeStrategy(Type.A);
        TypeStrategy b = new TypeStrategy(Type.B);
        StrategyRegistry<Type, TypeStrategy> registry = new StrategyRegistry<>(List.of(a, b));

        assertSame(a, registry.get(Type.A));
        assertSame(b, registry.get(Type.B));
        assertThrows(IllegalArgumentException.class, () -> registry.get(Type.C));
        assertThrows(IllegalArgumentException.class, () -> registry.get(null));
    }

    @Test
    void find() {
        TypeStrategy a = new TypeStrategy(Type.A);
        StrategyRegistry<Type, TypeStrategy> registry = new StrategyRegistry<>(List.of(a));

        assertEquals(a, registry.find(Type.A).orElseThrow());
        assertTrue(registry.find(Type.B).isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    void duplicateKey() {
        List<TypeStrategy> strategies = List.of(new TypeStrategy(Type.A), new TypeStrategy(Type.A));

        assertThrows(InvalidConfigurationException.class, () -> new StrategyRegistry<>(strategies));
    }

    @Test
    void nullKey() {
        List<TypeStrategy> strategies = List.of(new TypeStrategy(null));

        assertThrows(InvalidConfigurationException.class, () -> new StrategyRegistry<>(strategies));
    }
}
