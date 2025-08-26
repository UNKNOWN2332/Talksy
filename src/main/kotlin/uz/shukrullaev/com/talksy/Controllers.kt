package uz.shukrullaev.com.talksy

import org.springframework.http.MediaType
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

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

    @MessageMapping("me")
    fun me(): UserResponseDTO = userService.me()

    @MessageMapping("search")
    fun searchByUsername(@RequestParam username: String): List<UserResponseDTO> =
        userService.searchByUsername(username)

    @MessageMapping("profile")
    fun updateProfile(@RequestBody request: UserRequestDTO): UserResponseDTO =
        userService.updateProfile(request)

    @PostMapping("/upload", consumes = ["multipart/form-data"])
    fun uploadImage(
        @RequestParam("file") file: MultipartFile
    ): String = userService.uploadImage(file)
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
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun sendMessage(
        @RequestPart("payload") payload: MessageRequestDTO,
        @RequestPart("files", required = false) files: Array<MultipartFile>?
    ): MessageResponseDTO = messageService.sendMessage(payload, files?.toList())


    @GetMapping("/chat/{chatId}")
    fun getChatMessages(@PathVariable chatId: Long): List<MessageResponseDTO> =
        messageService.getChatMessages(chatId)
}