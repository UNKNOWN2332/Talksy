package uz.shukrullaev.com.talksy

import org.springframework.messaging.handler.annotation.*
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:09 pm
 */

@RestController
@RequestMapping("api/auth")
class TelegramLogin(
    private val userService: UserService

) {
    @PostMapping("/login")
    fun telegramCallback(
        @RequestBody userRequestDTO: UserRequestDTO
    ): TokenDTO = userService.saveOrUpdateFromTelegram(userRequestDTO)

    @PostMapping("/upload", consumes = ["multipart/form-data"])
    fun uploadImage(
        @RequestParam("file") file: MultipartFile
    ): String = userService.uploadImage(file)

}

@Controller
class TelegramAuthController(
    private val userService: UserService,
    private val jwtService: JwtService,
) {
    @MessageMapping("/me")
    @SendToUser("/queue/me")
    fun me(@Header("Authorization") authHeader: String?): UserResponseDTO {
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            throw TelegramDataIsNotValidException("Missing or invalid Authorization header in STOMP message")
        }
        val telegramId = jwtService.extractUserId(authHeader.removePrefix("Bearer ").trim())
        val user = userService.me(telegramId)
        return UserResponseDTO(user.id, "", user.username!!, "", "", "", Instant.now())
    }

    @MessageMapping("searchUser")
    fun searchByUsername(username: String): List<UserResponseDTO> =
        userService.searchByUsername(username)

    @MessageMapping("updateProfile")
    fun updateProfile(request: UserRequestDTO): UserResponseDTO =
        userService.updateProfile(request)
}

@RestController
@RequestMapping("/api/chats")
class ChatController(
    private val chatService: ChatService
) {
    @PostMapping
    fun createChat(@RequestBody dto: ChatRequestDTO): ChatResponseDTO =
        chatService.createChat(dto)

    @PostMapping("/direct")
    fun getOrCreateDirectChat(@RequestBody dto: ChatRequestDtoForUsers): ChatResponseDtoForUsers =
        chatService.getOrCreateDirectChat(dto)


    @GetMapping
    fun getMyChats(): List<ChatResponseDtoForUsers> =
        chatService.getMyChats()

    @GetMapping("chats")
    fun getChats(): List<ChatsWithNew> =
        chatService.getChats()
}

@Controller
class ChatWsController(
    private val chatService: ChatService
) {
    @MessageMapping("chat.create")//TODO need delete this method
    @SendToUser("/queue/chats")
    fun createChat(request: ChatRequestDTO): ChatResponseDTO =
        chatService.createChat(request)

    @MessageMapping("chat.direct")//TODO need delete this method
    @SendToUser("/queue/chats")
    fun getOrCreateDirectChat(request: ChatRequestDtoForUsers): ChatResponseDtoForUsers =
        chatService.getOrCreateDirectChat(request)

    @MessageMapping("chat.my")
    @SendToUser("/queue/chats")
    fun getMyChats(): List<ChatResponseDtoForUsers> =
        chatService.getMyChats()

    @MessageMapping("chat")
    fun getChats(): List<ChatsWithNew> =
        chatService.getChats()
}

@Controller
class MessageController(
    private val messageService: MessageService
) {
    @MessageMapping("/sendMessage")
    fun sendMessage(@Payload payload: MessageRequestDTO) {
        messageService.sendMessage(payload, null)
    }

    @MessageMapping("/getChatMessages")
    fun getChatMessages(request: ChatMessagesRequestDto) {
        messageService.getChatMessages(request)
    }
}

