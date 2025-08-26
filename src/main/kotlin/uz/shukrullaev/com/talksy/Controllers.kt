package uz.shukrullaev.com.talksy

import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:09 pm
 */

@Controller
class TelegramAuthController(
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

    @MessageMapping("me")
    @SendToUser("/queue/me")
    fun me(): UserResponseDTO = userService.me()

    // username orqali izlash
    @MessageMapping("search")
    @SendTo("/topic/search")
    fun searchByUsername(username: String): List<UserResponseDTO> =
        userService.searchByUsername(username)

    @MessageMapping("profile")
    @SendToUser("/queue/profile")
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
    @MessageMapping("chat.create")
    @SendToUser("/queue/chats")
    fun createChat(request: ChatRequestDTO): ChatResponseDTO =
        chatService.createChat(request)

    @MessageMapping("chat.direct")
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

    @MessageMapping("/getChat/{chatId}")
    fun getChatMessages(@DestinationVariable chatId: Long) {
        messageService.getChatMessages(chatId)
    }
}