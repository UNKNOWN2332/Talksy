package uz.shukrullaev.com.talksy


/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 02/09/2025 12:19 pm
 */

fun Boolean.runIfTrue(func: () -> Unit) {
    if (this) func()
}

fun Boolean.runIfFalse(func: () -> Unit) {
    if (!this) func()
}
