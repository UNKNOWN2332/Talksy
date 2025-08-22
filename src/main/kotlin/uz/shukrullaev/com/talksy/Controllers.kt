package uz.shukrullaev.com.talksy

import org.springframework.web.bind.annotation.*


/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:09 pm
 */


@RestController
@RequestMapping("api/auth")
class TelegramAuthController(
    private val userService: UserService

) {
    @PostMapping("login")
    fun telegramCallback(
        @RequestBody userRequestDTO: UserRequestDTO
    ): TokenDTO = userService.saveOrUpdateFromTelegram(userRequestDTO)

}

@RestController
@RequestMapping("/api/messages")
class MessageController(
    private val messageService: MessageService
) {
    @PostMapping
    fun sendMessage(@RequestBody dto: MessageRequestDTO): MessageResponseDTO =
        messageService.sendMessage(dto)


    @GetMapping("/chat")
    fun getMessages(@RequestParam username: String): List<MessageResponseDTO> =
        messageService.getMessages(username)


}

@RestController
@RequestMapping("/api/chats")
class ChatController(
    private val chatService: ChatService
) {
    @PostMapping
    fun createChat(@RequestBody dto: ChatRequestDTO): ChatResponseDTO =
        chatService.createChat(dto)


    @GetMapping
    fun getChat(@RequestParam username: String): List<ChatResponseDTO> =
        chatService.getUserChats(username)

}

