# Level 1: Array Dungeon

## Player's Journey:
1. See 5 empty boxes numbered 0-4
2. Drag Potion to box 0, Sword to 2, Shield to 4
3. Key appears → drag to box 1 (Sword moves to 3)
4. Door asks questions about boxes
5. Array full → choose: add slot? or remove item?
   - Add slot = wrong (lose health)
   - Remove item = choose what to sacrifice

## Learning:
- Only 5 boxes total (fixed size)
- Numbered 0-4 (not 1-5)
- Can't add box #6
- Must remove to insert when full

## Visuals:
- 5 stone alcoves
- Draggable items
- Health bar
- Door with buttons

## Code needed:
- Array that stays size 5
- Insert with shifting
- Remove to backpack
- Check if full