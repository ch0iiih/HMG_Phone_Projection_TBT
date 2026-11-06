.syntax unified
.thumb
.text
.p2align 2
.word 0x4c414e45
.thumb_func
guard_entry:
    push {r0-r3}
    ldr r3, [sp, #16]
    cbz r3, drop_null
    ldr r2, [r3, #4]
    movw r0, #65531
    cmp r2, r0
    bhi invalid_capacity
    adds r2, #4
    ldr r1, [r3, #8]
    ldr r0, [r3, #12]
    bl .Llane_notify_check
    cbnz r0, drop
    pop {r0-r3}
    adr.w r12, guard_notify
    ldr.w lr, [r12]
    add r12, lr
    blx r12
    b resume
invalid_capacity:
    movs r0, #1
drop:
    ldr.w r12, guard_log
    cmp.w r12, #0
    beq destroy
    lsrs r1, r0, #8
    uxtb r3, r0
    ldr r2, [sp, #16]
    ldr r0, [r2, #4]
    ldr r2, [r2, #8]
    sub sp, #16
    str r1, [sp]
    str r2, [sp, #4]
    str r0, [sp, #8]
    movs r0, #4
    adr.w r1, guard_tag
    adr.w r2, guard_message
    blx r12
    add sp, #16
destroy:
    pop {r0-r3}
    ldr r0, [sp]
    adr.w r12, guard_destroy
    ldr.w lr, [r12]
    add r12, lr
    blx r12
    b resume
drop_null:
    pop {r0-r3}
resume:
    adr.w r12, guard_resume
    ldr.w lr, [r12]
    add r12, lr
    bx r12
.p2align 2
guard_notify:  .word 0
guard_destroy: .word 0
guard_resume:  .word 0
guard_log:     .word 0
guard_original: .word 0
.thumb_func
lane_callback:
    push.w {r0-r4, lr}
    ldr.w r12, guard_log
    cmp.w r12, #0
    beq call_original
    movs r0, #4
    adr.w r1, guard_tag
    adr.w r2, callback_message
    blx r12
call_original:
    adr.w r12, guard_original
    ldr.w r4, [r12]
    add r12, r4
    pop.w {r0-r4, lr}
    bx r12
guard_tag: .asciz "HMG-API"
guard_message: .asciz "[wired-carplay-tbt-lx2pe] TBT lane guard drop reason=%u offset=%u written=%u capacity=%u"
callback_message: .asciz "[wired-carplay-tbt-lx2pe] TBT lane callback received bytes=%u"
.p2align 2
