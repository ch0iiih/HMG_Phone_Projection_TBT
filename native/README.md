# Lane guidance guard

The recipe is specific to the stock 260902 `libiap2session.so`. The first branch at `0x236bc` goes to `guard_entry`. The branch at `0x22cd0` goes to `lane_append_group`.

Compile the C with Clang targeting `armv7a-linux-androideabi19`, Thumb, `-O2 -ffreestanding -fno-stack-protector -fno-unwind-tables -S`. Append the generated bodies for `lane_notify_check`, `validate_group`, and `lane_append_group` to `lane-guard-entry.S`. Keep the validator names local and remove their unwind directives. Assemble with the same target. Extract the relocation-free `.text` bytes as hex for the recipe's `segment` line.

Use the assembled symbol offsets for the branches and relative slots. If the layout moves, update the offset checks in `LaneDispatchPatch.java` as well. The stock library is used at runtime and is not distributed here.
