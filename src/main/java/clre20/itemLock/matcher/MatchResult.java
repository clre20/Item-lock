package clre20.itemLock.matcher;

import clre20.itemLock.model.ItemTemplate;

import java.util.Optional;

/**
 * 物品比對結果。
 */
public record MatchResult(boolean matched, ItemTemplate template) {

    public static MatchResult matched(ItemTemplate template) {
        return new MatchResult(true, template);
    }

    public static MatchResult notMatched() {
        return new MatchResult(false, null);
    }

    public Optional<ItemTemplate> getTemplate() {
        return Optional.ofNullable(template);
    }
}
