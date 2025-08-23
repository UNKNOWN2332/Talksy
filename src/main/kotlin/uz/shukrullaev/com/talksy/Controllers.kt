package uz.shukrullaev.com.talksy

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.web.bind.annotation.*

/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:09 pm
 */

@RestController
@RequestMapping("/api/auth")
class TelegramAuthController(
    private val userService: UserService
) {
    @PostMapping("/login")
    fun telegramCallback(
        @RequestBody userRequestDTO: UserRequestDTO
    ): TokenDTO = userService.saveOrUpdateFromTelegram(userRequestDTO)

    @GetMapping("/me")
    fun me(): UserResponseDTO = userService.me()

    @GetMapping("/search")
    fun searchUsers(@RequestParam keyword: String): List<UserResponseDTO> =
        userService.searchUsers(keyword)

    @GetMapping("/{username}")
    fun findByUsername(@PathVariable username: String): UserResponseDTO? =
        userService.findByUsername(username)

    @PutMapping("/profile")
    fun updateProfile(@RequestBody request: UserRequestDTO): UserResponseDTO =
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
}
@RestController
@RequestMapping("/api/messages")
class MessageController(
    private val messageService: MessageService
) {
    @MessageMapping("Send.message")
    fun sendMessage(@RequestBody dto: MessageRequestDTO): MessageResponseDto =
        messageService.sendMessage(dto)

    @GetMapping("/chat/{chatId}")
    fun getChatMessages(@PathVariable chatId: Long): List<MessageResponseDto> =
        messageService.getChatMessages(chatId)
}