package com.iturtle.core.strategy;

import com.iturtle.core.exception.InvalidConfigurationException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 키로 전략을 찾는다. 생성 시 키가 null 이거나 겹치면 실패하므로 빈 등록 시점에 잘못된 구성이 드러난다.
 * <pre>
 * PaymentService(List&lt;PaymentStrategy&gt; strategies) {
 *     this.registry = new StrategyRegistry&lt;&gt;(strategies);
 * }
 *
 * registry.get(order.getType()).pay(order);
 * </pre>
 */
public final class StrategyRegistry<K, S extends Strategy<K>> {

    private final Map<K, S> strategies;

    /**
     * @throws InvalidConfigurationException 키가 null 이거나 두 구현체의 키가 같을 때
     */
    public StrategyRegistry(Collection<? extends S> strategies) {
        Map<K, S> map = new HashMap<>();
        for (S strategy : strategies) {
            K key = strategy.key();
            if (key == null) {
                throw new InvalidConfigurationException(
                        strategy.getClass().getName() + " 의 key() 가 null 입니다. 처리할 키를 반환하세요.");
            }
            S existing = map.putIfAbsent(key, strategy);
            if (existing != null) {
                throw new InvalidConfigurationException(
                        "전략 키 [" + key + "] 가 " + existing.getClass().getName() + " 와 "
                                + strategy.getClass().getName() + " 에 중복됩니다. 키는 구현체마다 달라야 합니다.");
            }
        }
        this.strategies = Collections.unmodifiableMap(map);
    }

    /**
     * 키에 해당하는 전략. 없으면 구현체를 빠뜨린 코드 버그로 보고 예외를 던진다.
     *
     * @throws IllegalArgumentException 키에 해당하는 전략이 없을 때
     */
    public S get(K key) {
        S strategy = strategies.get(key);
        if (strategy == null) {
            throw new IllegalArgumentException("전략 키 [" + key + "] 에 해당하는 구현체가 없습니다.");
        }
        return strategy;
    }

    /**
     * 키에 해당하는 전략. 없으면 빈 Optional.
     */
    public Optional<S> find(K key) {
        return Optional.ofNullable(strategies.get(key));
    }
}
