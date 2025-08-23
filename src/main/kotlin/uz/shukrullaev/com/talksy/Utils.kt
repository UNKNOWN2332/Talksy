package uz.shukrullaev.com.talksy

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 19/08/2025 6:35 pm
 */

object GlobalBotToken {
    lateinit var value: String
}

@Component
class BotConfig(@Value("\${telegram.bot.token}") botToken: String) {
    init {
        GlobalBotToken.value = botToken
        println("Bot token loaded: ${GlobalBotToken.value.take(5)}...") // check
    }
}


@Component
class HashUtils(){

    fun verifyTelegramHash(userRequestDTO: UserRequestDTO) {
        val params = mutableMapOf<String, String>()

        userRequestDTO.telegramId.let { params["id"] = it.toString() }
        userRequestDTO.firstName.let { params["first_name"] = it }
        userRequestDTO.lastName?.let { params["last_name"] = it }
        userRequestDTO.username?.let { params["username"] = it }
        userRequestDTO.photoUrl?.let { params["photo_url"] = it }
        userRequestDTO.authDate.let { params["auth_date"] = it.epochSecond.toString() }

        val dataCheckString = params.toSortedMap()
            .map { "${it.key}=${it.value}" }
            .joinToString("\n")

        val secretKey = MessageDigest.getInstance("SHA-256")
            .digest(GlobalBotToken.value.toByteArray(Charsets.UTF_8))

        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(secretKey, "HmacSHA256"))

        val calculatedHash = hmac.doFinal(dataCheckString.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        if (!calculatedHash.equals(userRequestDTO.hash, ignoreCase = true)) {
            throw TelegramDataIsNotValid(userRequestDTO)
        }
    }


}

