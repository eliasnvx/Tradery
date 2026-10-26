package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arithmetic every trade rests on: what is counted, taken and inserted, never more and never less. */
class StackMathTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.bootstrap();
    }

    private static List<ItemStack> slots(ItemStack... stacks) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : stacks) {
            list.add(stack.copy());
        }
        return list;
    }

    private static long total(List<ItemStack> slots) {
        return slots.stream().mapToLong(ItemStack::getCount).sum();
    }

    @Test
    void countsOnlyExactMatches() {
        ItemStack named = new ItemStack(Items.BREAD, 5);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Rye"));
        List<ItemStack> slots = slots(new ItemStack(Items.BREAD, 10), named, new ItemStack(Items.APPLE, 3), ItemStack.EMPTY);
        assertEquals(10, StackMath.count(slots, new ItemStack(Items.BREAD)));
        assertEquals(5, StackMath.count(slots, named));
        assertEquals(0, StackMath.count(slots, new ItemStack(Items.CARROT)));
    }

    @Test
    void takeNeverTakesMoreThanAskedOrPresent() {
        List<ItemStack> slots = slots(new ItemStack(Items.BREAD, 10), new ItemStack(Items.BREAD, 3));
        assertEquals(12, StackMath.take(slots, new ItemStack(Items.BREAD), 12));
        assertEquals(1, total(slots));
        assertEquals(1, StackMath.take(slots, new ItemStack(Items.BREAD), 5), "only what's there");
        assertEquals(0, total(slots));
        assertTrue(slots.stream().allMatch(ItemStack::isEmpty), "emptied slots are EMPTY, not zero-count stacks");
    }

    @Test
    void insertFillsExistingStacksFirstAndReportsTheRest() {
        List<ItemStack> slots = slots(new ItemStack(Items.BREAD, 60), ItemStack.EMPTY, new ItemStack(Items.APPLE, 1));
        assertEquals(68, StackMath.insert(slots, new ItemStack(Items.BREAD), 70), "64 - 60 into the first stack, 64 into the empty slot");
        assertEquals(64, slots.get(0).getCount());
        assertEquals(64, slots.get(1).getCount());
        assertEquals(1, slots.get(2).getCount(), "other items untouched");
        assertEquals(0, StackMath.space(slots, new ItemStack(Items.BREAD)));
    }

    @Test
    void spaceCountsEmptySlotsAndRoomInMatchingStacks() {
        List<ItemStack> slots = slots(new ItemStack(Items.BREAD, 60), ItemStack.EMPTY, new ItemStack(Items.APPLE, 1));
        assertEquals(4 + 64, StackMath.space(slots, new ItemStack(Items.BREAD)));
        assertEquals(64, StackMath.space(slots, new ItemStack(Items.CARROT)), "only the empty slot");
        assertEquals(0, StackMath.space(slots, ItemStack.EMPTY));
    }

    @Test
    void takeMatchingSparesSpecialStacksWhilePlainOnesLast() {
        ItemStack named = new ItemStack(Items.BREAD, 4);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Rye"));
        List<ItemStack> slots = slots(named, new ItemStack(Items.BREAD, 3));
        assertEquals(5, StackMath.countMatching(slots, stack -> stack.is(Items.BREAD) && stack.getCount() > 3) + 1);
        assertEquals(5, StackMath.takeMatching(slots, stack -> stack.is(Items.BREAD), 5));
        assertEquals(0, slots.get(1).getCount(), "the plain stack went first");
        assertEquals(2, slots.get(0).getCount(), "then two of the named one");
    }
}
