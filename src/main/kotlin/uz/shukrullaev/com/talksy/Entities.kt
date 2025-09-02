package uz.shukrullaev.com.talksy

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:08 pm
 */

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @CreatedDate @Column(updatable = false) var createdDate: Instant? = null,
    @LastModifiedDate var modifiedDate: Instant? = null,
    @Column(nullable = false) var deleted: Boolean = false,
    @CreatedBy @Column(updatable = false) var createdBy: String? = null,
    @LastModifiedBy var updatedBy: String? = null
)

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true) var telegramId: String,
    var username: String? = null,
    @Column(nullable = false) var firstName: String,
    var lastName: String? = null,
    var photoUrl: String? = null,
    var authDate: Instant? = null
) : BaseEntity()

@Entity
@Table(name = "app_files")
class AppFile(
    @Column(nullable = false) var ownerId: Long,
    @Column(nullable = false) var filePath: String,
    @Column(nullable = false, unique = true) var sha256Hash: String,
    @Column(nullable = false, unique = true) var customHash: String,
    @Column(nullable = false) var mimeType: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var type: MessageType = MessageType.TEXT,
    var size: Long? = null,
    var duration: Int? = null,
    var height: Int? = null,
    var width: Int? = null
) : BaseEntity()

@Entity
@Table(name = "chats")
class Chat(
    var title: String? = null,
    @Column(nullable = false) var isGroup: Boolean = false,
) : BaseEntity()

@Entity
@Table(name = "chat_users")
class ChatUser(
    @Column(nullable = false) var joinedDate: Instant,
    @Column(nullable = false) var isOwner: Boolean = false,
    @ManyToOne(optional = false) var chat: Chat,
    @ManyToOne(optional = false) var user: User
) : BaseEntity()

@Entity
@Table(name = "messages")
class Message(
    @ManyToOne(optional = false) var chat: Chat,
    @ManyToOne(optional = false) var sender: User,
    @ManyToOne var recipient: User? = null,
    @ManyToOne var replyTo: Message? = null,
    var caption: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var type: MessageType = MessageType.TEXT,
    @Lob var content: String? = null,
    @OneToMany(mappedBy = "message", cascade = [CascadeType.ALL], orphanRemoval = true)
    var attachments: MutableList<Attachment> = mutableListOf()
) : BaseEntity()

@Entity
@Table(name = "attachments")
class Attachment(
    @ManyToOne(optional = false) var file: AppFile,
    @ManyToOne(optional = false) var message: Message
) : BaseEntity()

@Entity
@Table(name = "message_statuses")
class MessageStatus(
    @ManyToOne(optional = false) var message: Message,
    @ManyToOne(optional = false) var user: User,
    @Enumerated(EnumType.STRING) var status: Status
) : BaseEntity()