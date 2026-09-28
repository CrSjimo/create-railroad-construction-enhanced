package dev.sjimo.rrce.platform;
import net.createmod.catnip.data.Couple;
public final class CreateCouple {
    public static <T> Couple<T> create(T first, T second) { return Couple.create(first, second); }
}
