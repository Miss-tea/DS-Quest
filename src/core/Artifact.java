
package core;

public enum Artifact {
    POTION("potion.png", "Potion"),
    KEY("key.png", "Key"),
    SWORD("sword.png", "Sword"),
    SHIELD("shield.png", "Shield"),
    TORCH("torch.png", "Torch"),
    POISON("magicalpotion.png", "Poison");

    private final String imageName;
    private final String displayName;

    Artifact(String imageName, String displayName) {
        this.imageName = imageName;
        this.displayName = displayName;
    }

    public String imageName() { return imageName; }
    public String displayName() { return displayName; }

    public static Artifact fromDisplayName(String n) {
        for (Artifact a : values()) {
            if (a.displayName.equalsIgnoreCase(n)) return a;
        }
        return null;
    }
}
