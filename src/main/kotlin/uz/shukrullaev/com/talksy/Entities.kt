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
@Table(name = "chats")
class Chat(
    var title: String? = null,
    @Column(nullable = false) var isGroup: Boolean,
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
    @Lob var content: String? = null,
    @ManyToOne(optional = false) var chat: Chat,
    @ManyToOne(optional = false) var sender: User,
    @ManyToOne var replyTo: Message? = null
) : BaseEntity()

@Entity
@Table(name = "attachments")
class Attachment(
    @Column(nullable = false) var url: String,
    @Column(nullable = false) var type: String,
    var size: Long? = null,
    var duration: Int? = null,
    @Column(nullable = false, unique = true) var fileHash: String? = null,
    @ManyToOne(optional = false) var message: Message
) : BaseEntity()

@Entity
class MessageStatus(
    @ManyToOne var message: Message,
    @ManyToOne var user: User,
    @Enumerated(EnumType.STRING)
    var status: Status
): BaseEntity()