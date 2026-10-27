package com.iturtle.core.strategy;

/**
 * 키로 선택되는 전략. 구현체는 자기가 처리할 키를 밝히고 {@link StrategyRegistry} 로 찾는다.
 * <pre>
 * public interface PaymentStrategy extends Strategy&lt;PaymentType&gt; {
 *     void pay(Order order);
 * }
 *
 * &#64;Component
 * class CardPaymentStrategy implements PaymentStrategy {
 *     public PaymentType key() { return PaymentType.CARD; }
 *     public void pay(Order order) { ... }
 * }
 * </pre>
 */
public interface Strategy<K> {

    /**
     * 이 전략이 처리하는 키. null 이면 안 되고 구현체마다 달라야 한다.
     */
    K key();
}
