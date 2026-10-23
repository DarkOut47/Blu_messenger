package com.example.umiumchat

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private val LANGS = listOf("Русский", "English", "Español", "Deutsch", "Français", "Português", "中文", "日本語", "Türkçe", "Українська")

private fun l(vararg s: String) = s.toList()

// Порядок переводов совпадает с LANGS
private val S: Map<String, List<String>> = mapOf(
    "chats" to l("Чаты", "Chats", "Chats", "Chats", "Discussions", "Conversas", "聊天", "チャット", "Sohbetler", "Чати"),
    "archive" to l("Архив", "Archive", "Archivo", "Archiv", "Archives", "Arquivo", "归档", "アーカイブ", "Arşiv", "Архів"),
    "profile" to l("Профиль", "Profile", "Perfil", "Profil", "Profil", "Perfil", "个人资料", "プロフィール", "Profil", "Профіль"),
    "settings" to l("Настройки", "Settings", "Ajustes", "Einstellungen", "Paramètres", "Configurações", "设置", "設定", "Ayarlar", "Налаштування"),
    "folders" to l("Папки с чатами", "Chat folders", "Carpetas", "Chat-Ordner", "Dossiers", "Pastas", "聊天文件夹", "チャットフォルダー", "Sohbet klasörleri", "Папки з чатами"),
    "language" to l("Язык", "Language", "Idioma", "Sprache", "Langue", "Idioma", "语言", "言語", "Dil", "Мова"),
    "search_title" to l("Поиск по @юзу", "Search by @username", "Buscar por @usuario", "Suche nach @Name", "Recherche par @pseudo", "Pesquisa por @usuário", "按 @用户名 搜索", "@ユーザー名で検索", "@kullanıcı adıyla ara", "Пошук за @юзом"),
    "search_hint" to l("Введите @юз", "Enter @username", "Escribe @usuario", "@Name eingeben", "Saisissez @pseudo", "Digite @usuário", "输入 @用户名", "@ユーザー名を入力", "@kullanıcı adını girin", "Введіть @юз"),
    "find" to l("Найти", "Find", "Buscar", "Finden", "Trouver", "Encontrar", "查找", "検索", "Bul", "Знайти"),
    "not_found" to l("не найден рядом", "not found nearby", "no encontrado cerca", "nicht in der Nähe", "introuvable à proximité", "não encontrado por perto", "附近未找到", "近くに見つかりません", "yakında bulunamadı", "не знайдено поруч"),
    "nearby_now" to l("Сейчас рядом", "Nearby now", "Cerca ahora", "In der Nähe", "À proximité", "Perto de você", "附近", "近くの人", "Yakında", "Поруч зараз"),
    "people_nearby" to l("Людей рядом", "People nearby", "Personas cerca", "Leute in der Nähe", "Personnes à proximité", "Pessoas por perto", "附近的人", "近くの人", "Yakındaki kişiler", "Людей поруч"),
    "nobody_nearby" to l("Рядом никого нет.", "No one nearby.", "Nadie cerca.", "Niemand in der Nähe.", "Personne à proximité.", "Ninguém por perto.", "附近没有人。", "近くに誰もいません。", "Yakında kimse yok.", "Поруч нікого немає."),
    "no_chats" to l("Чатов пока нет.\nНажмите 🔍 и найдите человека рядом.", "No chats yet.\nTap 🔍 to find someone nearby.", "Aún no hay chats.\nToca 🔍 para buscar.", "Noch keine Chats.\nTippe auf 🔍.", "Pas encore de discussions.\nAppuyez sur 🔍.", "Ainda não há conversas.\nToque em 🔍.", "还没有聊天。\n点击 🔍 查找附近的人。", "まだチャットはありません。\n🔍 をタップしてください。", "Henüz sohbet yok.\n🔍 simgesine dokunun.", "Чатів поки немає.\nНатисніть 🔍 і знайдіть людину поруч."),
    "archive_empty" to l("В архиве пусто.", "Archive is empty.", "El archivo está vacío.", "Das Archiv ist leer.", "L'archive est vide.", "O arquivo está vazio.", "归档为空。", "アーカイブは空です。", "Arşiv boş.", "Архів порожній."),
    "you" to l("Вы", "You", "Tú", "Du", "Vous", "Você", "你", "あなた", "Siz", "Ви"),
    "photo" to l("Фото", "Photo", "Foto", "Foto", "Photo", "Foto", "照片", "写真", "Fotoğraf", "Фото"),
    "message_hint" to l("Сообщение", "Message", "Mensaje", "Nachricht", "Message", "Mensagem", "消息", "メッセージ", "Mesaj", "Повідомлення"),
    "online" to l("в сети", "online", "en línea", "online", "en ligne", "online", "在线", "オンライン", "çevrimiçi", "в мережі"),
    "offline" to l("не в сети", "offline", "desconectado", "offline", "hors ligne", "offline", "离线", "オフライン", "çevrimdışı", "не в мережі"),
    "nearby_word" to l("рядом", "nearby", "cerca", "in der Nähe", "à proximité", "por perto", "附近", "近く", "yakında", "поруч"),
    "edited" to l("(изм.)", "(edited)", "(editado)", "(bearb.)", "(modifié)", "(editado)", "(已编辑)", "(編集済み)", "(düzenlendi)", "(змінено)"),
    "forwarded_from" to l("Переслано от", "Forwarded from", "Reenviado de", "Weitergeleitet von", "Transféré de", "Encaminhado de", "转发自", "転送元", "Şuradan iletildi", "Переслано від"),
    "delete_for_me" to l("Удалить у меня", "Delete for me", "Eliminar para mí", "Für mich löschen", "Supprimer pour moi", "Excluir para mim", "仅为我删除", "自分のみ削除", "Benim için sil", "Видалити у мене"),
    "delete_for_both" to l("Удалить у меня и у собеседника", "Delete for everyone", "Eliminar para todos", "Für alle löschen", "Supprimer pour tous", "Excluir para todos", "为所有人删除", "全員から削除", "Herkes için sil", "Видалити у мене і співрозмовника"),
    "delete" to l("Удалить", "Delete", "Eliminar", "Löschen", "Supprimer", "Excluir", "删除", "削除", "Sil", "Видалити"),
    "pin" to l("Закрепить", "Pin", "Fijar", "Anheften", "Épingler", "Fixar", "置顶", "ピン留め", "Sabitle", "Закріпити"),
    "unpin" to l("Открепить", "Unpin", "Desfijar", "Lösen", "Désépingler", "Desafixar", "取消置顶", "ピン留めを外す", "Sabitlemeyi kaldır", "Відкріпити"),
    "to_archive" to l("В архив", "Archive", "Archivar", "Archivieren", "Archiver", "Arquivar", "归档", "アーカイブ", "Arşivle", "В архів"),
    "from_archive" to l("Вернуть из архива", "Unarchive", "Desarchivar", "Aus Archiv holen", "Désarchiver", "Desarquivar", "取消归档", "アーカイブ解除", "Arşivden çıkar", "Повернути з архіву"),
    "to_folder" to l("В папку", "Add to folder", "Añadir a carpeta", "In Ordner", "Ajouter au dossier", "Adicionar à pasta", "添加到文件夹", "フォルダーに追加", "Klasöre ekle", "До папки"),
    "no_folders" to l("Сначала создайте папку", "Create a folder first", "Crea una carpeta primero", "Erstelle zuerst einen Ordner", "Créez d'abord un dossier", "Crie uma pasta primeiro", "请先创建文件夹", "先にフォルダーを作成してください", "Önce klasör oluşturun", "Спочатку створіть папку"),
    "reply" to l("Ответить", "Reply", "Responder", "Antworten", "Répondre", "Responder", "回复", "返信", "Yanıtla", "Відповісти"),
    "forward" to l("Переслать", "Forward", "Reenviar", "Weiterleiten", "Transférer", "Encaminhar", "转发", "転送", "İlet", "Переслати"),
    "copy" to l("Копировать", "Copy", "Copiar", "Kopieren", "Copier", "Copiar", "复制", "コピー", "Kopyala", "Копіювати"),
    "edit" to l("Изменить", "Edit", "Editar", "Bearbeiten", "Modifier", "Editar", "编辑", "編集", "Düzenle", "Змінити"),
    "copied" to l("Скопировано", "Copied", "Copiado", "Kopiert", "Copié", "Copiado", "已复制", "コピーしました", "Kopyalandı", "Скопійовано"),
    "forwarded" to l("Переслано", "Forwarded", "Reenviado", "Weitergeleitet", "Transféré", "Encaminhado", "已转发", "転送しました", "İletildi", "Переслано"),
    "no_chats_forward" to l("Нет чатов для пересылки", "No chats to forward to", "No hay chats para reenviar", "Keine Chats zum Weiterleiten", "Aucune discussion", "Nenhuma conversa", "没有可转发的聊天", "転送先がありません", "İletilecek sohbet yok", "Немає чатів для пересилання"),
    "reply_label" to l("Ответ", "Reply", "Respuesta", "Antwort", "Réponse", "Resposta", "回复", "返信", "Yanıt", "Відповідь"),
    "editing" to l("Изменение сообщения", "Editing message", "Editando mensaje", "Nachricht bearbeiten", "Modification du message", "Editando mensagem", "正在编辑消息", "メッセージを編集中", "Mesaj düzenleniyor", "Зміна повідомлення"),
    "profile_sub" to l("Ваш аккаунт", "Your account", "Tu cuenta", "Ihr Konto", "Votre compte", "Sua conta", "你的账户", "アカウント", "Hesabınız", "Ваш акаунт"),
    "username_label" to l("Ваш @юз", "Your @username", "Tu @usuario", "Dein @Name", "Votre @pseudo", "Seu @usuário", "你的 @用户名", "あなたの @ユーザー名", "Kullanıcı adınız", "Ваш @юз"),
    "username_err" to l("Юз: от 3 до 32 символов, латиница, цифры и _", "Username: 3–32 chars, latin letters, digits and _", "Usuario: 3–32 caracteres, letras, números y _", "Name: 3–32 Zeichen, Buchstaben, Ziffern und _", "Pseudo : 3–32 caractères, lettres, chiffres et _", "Usuário: 3–32 caracteres, letras, números e _", "用户名：3–32 个字符，字母、数字和 _", "ユーザー名：3〜32文字、英数字と _", "Kullanıcı adı: 3–32 karakter, harf, rakam ve _", "Юз: від 3 до 32 символів, латиниця, цифри та _"),
    "username_short" to l("Юз", "Username", "Usuario", "Benutzername", "Pseudo", "Usuário", "用户名", "ユーザー名", "Kullanıcı adı", "Юз"),
    "not_set" to l("не задан", "not set", "sin definir", "nicht gesetzt", "non défini", "não definido", "未设置", "未設定", "ayarlanmadı", "не задано"),
    "account" to l("Аккаунт", "Account", "Cuenta", "Konto", "Compte", "Conta", "账户", "アカウント", "Hesap", "Акаунт"),
    "delete_account" to l("Удалить аккаунт", "Delete account", "Eliminar cuenta", "Konto löschen", "Supprimer le compte", "Excluir conta", "删除账户", "アカウントを削除", "Hesabı sil", "Видалити акаунт"),
    "delete_account_msg" to l("Юз, чаты и настройки будут стёрты с телефона.", "Username, chats and settings will be erased.", "Usuario, chats y ajustes se borrarán.", "Name, Chats und Einstellungen werden gelöscht.", "Pseudo, discussions et réglages seront effacés.", "Usuário, conversas e ajustes serão apagados.", "用户名、聊天和设置将被删除。", "ユーザー名、チャット、設定が削除されます。", "Kullanıcı adı, sohbetler ve ayarlar silinecek.", "Юз, чати та налаштування буде стерто."),
    "chat_settings" to l("Настройки чатов", "Chat settings", "Ajustes del chat", "Chat-Einstellungen", "Paramètres des discussions", "Configurações de conversas", "聊天设置", "チャット設定", "Sohbet ayarları", "Налаштування чатів"),
    "privacy" to l("Конфиденциальность", "Privacy", "Privacidad", "Datenschutz", "Confidentialité", "Privacidade", "隐私", "プライバシー", "Gizlilik", "Конфіденційність"),
    "notifications" to l("Уведомления", "Notifications", "Notificaciones", "Benachrichtigungen", "Notifications", "Notificações", "通知", "通知", "Bildirimler", "Сповіщення"),
    "wallpaper" to l("Фон чата (только для вас)", "Chat background (only you)", "Fondo del chat (solo tú)", "Chat-Hintergrund (nur du)", "Fond (vous seul)", "Fundo do chat (só você)", "聊天背景（仅自己）", "チャット背景（自分のみ）", "Sohbet arka planı (yalnızca sen)", "Фон чату (лише для вас)"),
    "text_size" to l("Размер текста", "Text size", "Tamaño del texto", "Textgröße", "Taille du texte", "Tamanho do texto", "文字大小", "文字サイズ", "Yazı boyutu", "Розмір тексту"),
    "dark_theme" to l("Тёмная тема", "Dark theme", "Tema oscuro", "Dunkles Design", "Thème sombre", "Tema escuro", "深色主题", "ダークテーマ", "Koyu tema", "Темна тема"),
    "app_icon" to l("Иконка приложения", "App icon", "Icono de la app", "App-Symbol", "Icône de l'app", "Ícone do app", "应用图标", "アプリアイコン", "Uygulama simgesi", "Іконка застосунку"),
    "unavailable" to l("Пока недоступно", "Not available yet", "Aún no disponible", "Noch nicht verfügbar", "Pas encore disponible", "Ainda não disponível", "暂不可用", "まだ利用できません", "Henüz kullanılamıyor", "Поки недоступно"),
    "passcode" to l("Пароль для входа", "Passcode lock", "Bloqueo con código", "Code-Sperre", "Verrouillage par code", "Bloqueio por código", "密码锁", "パスコードロック", "Parola kilidi", "Пароль для входу"),
    "on" to l("включён", "on", "activado", "an", "activé", "ativado", "已开启", "オン", "açık", "увімкнено"),
    "off" to l("выключен", "off", "desactivado", "aus", "désactivé", "desativado", "已关闭", "オフ", "kapalı", "вимкнено"),
    "blacklist" to l("Чёрный список", "Blocked list", "Lista de bloqueados", "Blockliste", "Liste de blocage", "Lista de bloqueados", "黑名单", "ブロックリスト", "Engellenenler", "Чорний список"),
    "blocked_count" to l("Заблокировано", "Blocked", "Bloqueados", "Blockiert", "Bloqués", "Bloqueados", "已屏蔽", "ブロック中", "Engellenen", "Заблоковано"),
    "last_seen" to l("Время захода", "Last seen", "Última vez", "Zuletzt online", "Dernière connexion", "Visto por último", "上次在线", "最終オンライン", "Son görülme", "Час останнього входу"),
    "fwd_privacy" to l("Пересылка сообщений", "Message forwarding", "Reenvío de mensajes", "Weiterleitung", "Transfert de messages", "Encaminhamento", "消息转发", "メッセージの転送", "Mesaj iletme", "Пересилання повідомлень"),
    "who_writes" to l("Кто может начинать со мной общение?", "Who can message me?", "¿Quién puede escribirme?", "Wer darf mir schreiben?", "Qui peut m'écrire ?", "Quem pode me escrever?", "谁可以给我发消息？", "誰がメッセージを送れますか？", "Bana kim yazabilir?", "Хто може писати мені першим?"),
    "everyone" to l("Все", "Everyone", "Todos", "Alle", "Tous", "Todos", "所有人", "全員", "Herkes", "Усі"),
    "nobody" to l("Никто", "Nobody", "Nadie", "Niemand", "Personne", "Ninguém", "没有人", "誰も", "Hiç kimse", "Ніхто"),
    "contacts_only" to l("Только мои контакты", "Only my contacts", "Solo mis contactos", "Nur meine Kontakte", "Mes contacts uniquement", "Somente meus contatos", "仅我的联系人", "連絡先のみ", "Yalnızca kişilerim", "Лише мої контакти"),
    "nearby_only" to l("Все рядом", "Everyone nearby", "Todos cerca", "Alle in der Nähe", "Tous à proximité", "Todos por perto", "附近的所有人", "近くの全員", "Yakındaki herkes", "Усі поруч"),
    "volume" to l("Громкость уведомлений", "Notification volume", "Volumen de notificaciones", "Benachrichtigungslautstärke", "Volume des notifications", "Volume das notificações", "通知音量", "通知の音量", "Bildirim sesi", "Гучність сповіщень"),
    "notif_sound" to l("Звук уведомлений", "Notification sound", "Sonido de notificaciones", "Benachrichtigungston", "Son des notifications", "Som de notificações", "通知声音", "通知音", "Bildirim sesi", "Звук сповіщень"),
    "tone" to l("Мелодия уведомлений", "Notification tone", "Tono de notificación", "Benachrichtigungston", "Son de notification", "Toque de notificação", "通知铃声", "通知音", "Bildirim melodisi", "Мелодія сповіщень"),
    "default_word" to l("По умолчанию", "Default", "Predeterminado", "Standard", "Par défaut", "Padrão", "默认", "デフォルト", "Varsayılan", "За замовчуванням"),
    "custom_word" to l("Выбрана своя", "Custom", "Personalizada", "Eigene", "Personnalisée", "Personalizada", "自定义", "カスタム", "Özel", "Власна"),
    "tone_saved" to l("Мелодия сохранена", "Tone saved", "Tono guardado", "Ton gespeichert", "Son enregistré", "Toque salvo", "铃声已保存", "通知音を保存しました", "Melodi kaydedildi", "Мелодію збережено"),
    "folder_new" to l("Создать папку", "Create folder", "Crear carpeta", "Ordner erstellen", "Créer un dossier", "Criar pasta", "创建文件夹", "フォルダーを作成", "Klasör oluştur", "Створити папку"),
    "folder_name" to l("Название папки", "Folder name", "Nombre de la carpeta", "Ordnername", "Nom du dossier", "Nome da pasta", "文件夹名称", "フォルダー名", "Klasör adı", "Назва папки"),
    "own_folders" to l("Свои папки", "Your folders", "Tus carpetas", "Eigene Ordner", "Vos dossiers", "Suas pastas", "你的文件夹", "マイフォルダー", "Klasörleriniz", "Ваші папки"),
    "folder_tap_delete" to l("нажмите, чтобы удалить", "tap to delete", "toca para eliminar", "zum Löschen tippen", "appuyez pour supprimer", "toque para excluir", "点击删除", "タップで削除", "silmek için dokunun", "натисніть, щоб видалити"),
    "block" to l("Заблокировать", "Block", "Bloquear", "Blockieren", "Bloquer", "Bloquear", "屏蔽", "ブロック", "Engelle", "Заблокувати"),
    "unblock_hint" to l("нажмите, чтобы разблокировать", "tap to unblock", "toca para desbloquear", "zum Entsperren tippen", "appuyez pour débloquer", "toque para desbloquear", "点击解除屏蔽", "タップでブロック解除", "engeli kaldırmak için dokunun", "натисніть, щоб розблокувати"),
    "list_empty" to l("Список пуст", "List is empty", "La lista está vacía", "Die Liste ist leer", "La liste est vide", "A lista está vazia", "列表为空", "リストは空です", "Liste boş", "Список порожній"),
    "chat_all" to l("Все", "All", "Todos", "Alle", "Tous", "Todos", "全部", "すべて", "Tümü", "Усі"),
    "set_pin" to l("Придумайте пароль (4 цифры)", "Create a passcode (4 digits)", "Crea un código (4 dígitos)", "Code festlegen (4 Ziffern)", "Créez un code (4 chiffres)", "Crie um código (4 dígitos)", "设置密码（4位数字）", "パスコードを設定（4桁）", "Parola oluşturun (4 rakam)", "Придумайте пароль (4 цифри)"),
    "repeat_pin" to l("Повторите пароль", "Repeat passcode", "Repite el código", "Code wiederholen", "Répétez le code", "Repita o código", "再次输入密码", "もう一度入力", "Parolayı tekrarlayın", "Повторіть пароль"),
    "enter_current" to l("Введите текущий пароль", "Enter current passcode", "Introduce el código actual", "Aktuellen Code eingeben", "Saisissez le code actuel", "Digite o código atual", "输入当前密码", "現在のパスコードを入力", "Mevcut parolayı girin", "Введіть поточний пароль"),
    "wrong_pin" to l("Неверный пароль", "Wrong passcode", "Código incorrecto", "Falscher Code", "Code incorrect", "Código incorreto", "密码错误", "パスコードが違います", "Parola yanlış", "Невірний пароль"),
    "pin_mismatch" to l("Пароли не совпали", "Passcodes don't match", "Los códigos no coinciden", "Codes stimmen nicht überein", "Les codes ne correspondent pas", "Os códigos não coincidem", "两次密码不一致", "パスコードが一致しません", "Parolalar eşleşmiyor", "Паролі не збігаються"),
    "locked_title" to l("BLEmess заблокирован", "BLEmess is locked", "BLEmess está bloqueado", "BLEmess ist gesperrt", "BLEmess est verrouillé", "BLEmess está bloqueado", "BLEmess 已锁定", "BLEmess はロックされています", "BLEmess kilitli", "BLEmess заблоковано"),
    "save" to l("Сохранить", "Save", "Guardar", "Speichern", "Enregistrer", "Salvar", "保存", "保存", "Kaydet", "Зберегти"),
    "net_err" to l("Ошибка сети", "Network error", "Error de red", "Netzwerkfehler", "Erreur réseau", "Erro de rede", "网络错误", "ネットワークエラー", "Ağ hatası", "Помилка мережі")
)

class MainActivity : Activity() {

    private enum class Screen {
        CHATS, CHAT, PROFILE, SETTINGS, SEARCH, ACCOUNT, CHAT_SETTINGS,
        PRIVACY, NOTIFY, FOLDERS, LANGUAGE, BLACKLIST
    }

    private data class Msg(
        val id: String,
        val from: String,
        var text: String,
        val mine: Boolean,
        var read: Boolean = false,
        var edited: Boolean = false,
        val reply: String = "",
        val fwd: String = "",
        var unread: Boolean = false,
        val img: String = ""
    )

    private class Btn(val text: String, val color: Int, val action: () -> Unit)

    private val serviceId = "com.example.umiumchat"
    private val prefs by lazy { getSharedPreferences("blemess", Context.MODE_PRIVATE) }
    private val client by lazy { Nearby.getConnectionsClient(this) }
    private val walls = listOf("", "#D6EAF8", "#E5DDD5", "#D5F5E3", "#2C3E50")

    private var myId = ""
    private var dark = false
    private var unlocked = false
    private var screen = Screen.CHATS
    private var openPeer = ""
    private var archiveView = false
    private var folderFilter: String? = null
    private var editingId: String? = null
    private var replyPreview = ""
    private var drawerOpen = false

    private val nearby = mutableMapOf<String, String>()
    private val links = mutableMapOf<String, String>()
    private val pending = mutableSetOf<String>()
    private val seen = HashSet<String>()
    private val chats = LinkedHashMap<String, MutableList<Msg>>()
    private val metaByFile = HashMap<Long, Pair<String, JSONObject>>()
    private val fileByFile = HashMap<Long, Pair<String, File>>()

    private var headerSub: TextView? = null
    private var listBox: LinearLayout? = null
    private var msgBox: LinearLayout? = null
    private var chatScroll: ScrollView? = null
    private var nearbyBox: LinearLayout? = null
    private var inputField: EditText? = null
    private var hintBar: LinearLayout? = null
    private var hintText: TextView? = null
    private var drawerScrim: View? = null
    private var drawerPanel: LinearLayout? = null

    private var username: String
        get() = prefs.getString("username", "") ?: ""
        set(v) { prefs.edit().putString("username", v).apply() }
    private val myName: String get() = username.ifEmpty { myId }
    private val lang: Int get() = prefs.getInt("langIdx", 0)
    private val fontSize: Int get() = prefs.getInt("fontSize", 16)

    private fun t(key: String): String {
        val arr = S[key] ?: return key
        return arr.getOrElse(lang) { arr[0] }
    }

    private val bg get() = if (dark) Color.parseColor("#17212B") else Color.WHITE
    private val barColor get() = if (dark) Color.parseColor("#232E3C") else Color.parseColor("#517DA2")
    private val textColor get() = if (dark) Color.WHITE else Color.BLACK
    private val subColor get() = if (dark) Color.parseColor("#8A99A8") else Color.parseColor("#7F8C99")
    private val sepColor get() = if (dark) Color.parseColor("#253341") else Color.parseColor("#E6E8EB")
    private val pinBg get() = if (dark) Color.parseColor("#1E2D3D") else Color.parseColor("#EAF3FB")
    private val myBubble get() = if (dark) Color.parseColor("#2B5278") else Color.parseColor("#EFFDDE")
    private val otherBubble get() = if (dark) Color.parseColor("#182533") else Color.WHITE
    private val inputBg get() = if (dark) Color.parseColor("#17212B") else Color.WHITE
    private val popupBg get() = if (dark) Color.parseColor("#2B3A4A") else Color.WHITE
    private val chipOff get() = if (dark) Color.parseColor("#232E3C") else Color.parseColor("#E6E8EB")
    private val chatBg get() = prefs.getInt("wall", 0).let { if (it == 0) bg else Color.parseColor(walls[it]) }

    // ---------- Жизненный цикл ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        myId = prefs.getString("id", null) ?: newId()
        dark = prefs.getBoolean("dark", false)
        loadChats()
        requestPermissions(
            arrayOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.NEARBY_WIFI_DEVICES"
            ), 1
        )
        if ((prefs.getString("pin", "") ?: "").isEmpty()) {
            unlocked = true
            render()
        } else {
            showLock()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startMesh()
        if (unlocked && username.isEmpty()) go(Screen.PROFILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == 42) {
            val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            setStr("sound", uri?.toString() ?: "")
            toast(t("tone_saved"))
            go(Screen.NOTIFY)
        } else if (requestCode == 43) {
            val uri = data.data ?: return
            sendPhotoFromUri(uri)
        }
    }

    override fun onBackPressed() {
        if (!unlocked) { super.onBackPressed(); return }
        if (drawerOpen) { closeDrawer(); return }
        when (screen) {
            Screen.CHATS -> if (archiveView) { archiveView = false; go(Screen.CHATS) } else super.onBackPressed()
            Screen.CHAT, Screen.PROFILE, Screen.SETTINGS, Screen.SEARCH -> go(Screen.CHATS)
            Screen.BLACKLIST -> go(Screen.PRIVACY)
            else -> go(Screen.SETTINGS)
        }
    }

    override fun onDestroy() {
        saveChats()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        super.onDestroy()
    }

    private fun newId(): String {
        val id = UUID.randomUUID().toString().take(6)
        prefs.edit().putString("id", id).apply()
        return id
    }

    // ---------- Навигация и обновления ----------
    private fun go(s: Screen) {
        screen = s
        render()
    }

    private fun render() {
        headerSub = null; listBox = null; msgBox = null; chatScroll = null
        nearbyBox = null; inputField = null; hintBar = null; hintText = null
        drawerScrim = null; drawerPanel = null; drawerOpen = false
        when (screen) {
            Screen.CHATS -> buildChats()
            Screen.CHAT -> buildChat()
            Screen.PROFILE -> buildProfile()
            Screen.SETTINGS -> buildSettings()
            Screen.SEARCH -> buildSearch()
            Screen.ACCOUNT -> buildAccount()
            Screen.CHAT_SETTINGS -> buildChatSettings()
            Screen.PRIVACY -> buildPrivacy()
            Screen.NOTIFY -> buildNotify()
            Screen.FOLDERS -> buildFolders()
            Screen.LANGUAGE -> buildLanguage()
            Screen.BLACKLIST -> buildBlacklist()
        }
    }

    private fun refreshLive() {
        saveChats()
        ui {
            when (screen) {
                Screen.CHATS -> fillChatList()
                Screen.CHAT -> { fillMessages(); fillHint() }
                Screen.SEARCH -> fillNearby()
                else -> {}
            }
        }
    }

    private fun ui(block: () -> Unit) = runOnUiThread { block() }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    // ---------- Общие элементы ----------
    private fun screenRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
    }

    private fun show(root: LinearLayout) {
        if (screen == Screen.CHATS || screen == Screen.PROFILE || screen == Screen.SETTINGS) {
            root.addView(bottomBar())
        }
        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        if (screen == Screen.CHATS) attachDrawer(frame)
        setContentView(frame)
        root.alpha = 0f
        root.translationX = dp(24).toFloat()
        root.animate().alpha(1f).translationX(0f).setDuration(220).start()
    }

    private fun bottomBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(barColor)
        bar.addView(tabButton(t("chats"), Screen.CHATS))
        bar.addView(tabButton(t("profile"), Screen.PROFILE))
        bar.addView(tabButton(t("settings"), Screen.SETTINGS))
        return bar
    }

    private fun tabButton(caption: String, target: Screen): TextView {
        val active = screen == target
        val tv = TextView(this)
        tv.text = caption
        tv.gravity = Gravity.CENTER
        tv.textSize = 14f
        tv.setPadding(0, dp(14), 0, dp(14))
        tv.setTextColor(if (active) Color.WHITE else Color.parseColor("#D6E4F0"))
        tv.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tv.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        tv.setOnClickListener { archiveView = false; go(target) }
        return tv
    }

    private fun attachDrawer(frame: FrameLayout) {
        val width = dp(280)
        val scrim = View(this)
        scrim.setBackgroundColor(Color.BLACK)
        scrim.alpha = 0f
        scrim.visibility = View.GONE
        scrim.setOnClickListener { closeDrawer() }
        frame.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(bg)
        panel.setPadding(0, dp(36), 0, dp(16))
        panel.translationX = -width.toFloat()

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(18), dp(10), dp(18), dp(20))
        head.addView(avatar(myName), LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(12) })
        val who = TextView(this)
        who.text = "@$myName"
        who.setTextColor(textColor)
        who.textSize = 17f
        who.typeface = Typeface.DEFAULT_BOLD
        head.addView(who)
        panel.addView(head)
        addSeparator(panel)

        panel.addView(drawerItem(t("archive")) { archiveView = true; go(Screen.CHATS) })
        panel.addView(drawerItem(t("profile")) { go(Screen.PROFILE) })
        panel.addView(drawerItem(t("folders")) { go(Screen.FOLDERS) })
        panel.addView(drawerItem(t("settings")) { go(Screen.SETTINGS) })

        frame.addView(panel, FrameLayout.LayoutParams(width, -1, Gravity.START))
        drawerScrim = scrim
        drawerPanel = panel
    }

    private fun drawerItem(title: String, action: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(textColor)
        tv.textSize = 16f
        tv.setPadding(dp(18), dp(14), dp(18), dp(14))
        tv.setOnClickListener { drawerOpen = false; action() }
        return tv
    }

    private fun openDrawer() {
        val scrim = drawerScrim ?: return
        val panel = drawerPanel ?: return
        drawerOpen = true
        scrim.visibility = View.VISIBLE
        scrim.animate().alpha(0.45f).setDuration(220).start()
        panel.animate().translationX(0f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun closeDrawer() {
        val scrim = drawerScrim ?: return
        val panel = drawerPanel ?: return
        drawerOpen = false
        panel.animate().translationX(-dp(280).toFloat()).setDuration(220).start()
        scrim.animate().alpha(0f).setDuration(220).withEndAction { scrim.visibility = View.GONE }.start()
    }

    private fun barButton(b: Btn): Button {
        val btn = Button(this)
        btn.text = b.text
        btn.setTextColor(Color.WHITE)
        btn.textSize = 16f
        btn.isAllCaps = false
        btn.setBackgroundColor(b.color)
        btn.setPadding(dp(10), 0, dp(10), 0)
        btn.setOnClickListener { b.action() }
        return btn
    }

    private fun backBtn(target: Screen) = Btn("←", Color.TRANSPARENT) { go(target) }

    private fun titleBar(title: String, subtitle: String, left: Btn?, right: Btn?): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(barColor)
        bar.setPadding(dp(4), dp(10), dp(4), dp(10))

        left?.let { bar.addView(barButton(it)) }

        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.gravity = Gravity.CENTER

        val titleView = TextView(this)
        titleView.text = title
        titleView.setTextColor(Color.WHITE)
        titleView.textSize = 18f
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.gravity = Gravity.CENTER

        val sub = TextView(this)
        sub.text = subtitle
        sub.setTextColor(Color.parseColor("#D6E4F0"))
        sub.textSize = 12f
        sub.gravity = Gravity.CENTER
        headerSub = sub

        titles.addView(titleView)
        titles.addView(sub)
        bar.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))

        right?.let { bar.addView(barButton(it)) }
        return bar
    }

    private fun page(title: String, left: Btn? = null, right: Btn? = null, sub: String = "",
                     fill: (LinearLayout) -> Unit) {
        val root = screenRoot()
        root.addView(titleBar(title, sub, left, right))
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(bg)
        content.setPadding(0, 0, 0, dp(12))
        fill(content)
        val scroll = ScrollView(this)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        show(root)
    }

    private fun label(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.setTextColor(subColor)
        tv.textSize = 13f
        tv.setPadding(dp(18), dp(16), dp(18), dp(6))
        return tv
    }

    private fun smallText(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.setTextColor(subColor)
        tv.textSize = 13f
        return tv
    }

    private fun emptyText(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.setTextColor(subColor)
        tv.textSize = 15f
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(32), dp(60), dp(32), dp(32))
        return tv
    }

    private fun avatar(name: String): TextView {
        val palette = listOf("#E17076", "#7BC862", "#65AADD", "#A695E7", "#EE7AAE", "#6EC9CB", "#FAA774")
        val tv = TextView(this)
        tv.text = name.firstOrNull()?.uppercase() ?: "?"
        tv.setTextColor(Color.WHITE)
        tv.textSize = 20f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        val shape = GradientDrawable()
        shape.setShape(GradientDrawable.OVAL)
        shape.setColor(Color.parseColor(palette[Math.abs(name.hashCode()) % palette.size]))
        tv.background = shape
        return tv
    }

    private fun dot(): View {
        val v = View(this)
        val shape = GradientDrawable()
        shape.setShape(GradientDrawable.OVAL)
        shape.setColor(Color.parseColor("#4CAF50"))
        shape.setStroke(dp(2), bg)
        v.background = shape
        return v
    }

    private fun addSeparator(c: LinearLayout) {
        val line = View(this)
        line.setBackgroundColor(sepColor)
        c.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun addRow(c: LinearLayout, v: View) {
        c.addView(v)
        addSeparator(c)
    }

    private fun navRow(title: String, sub: String = "", danger: Boolean = false, onClick: (View) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(dp(18), dp(12), dp(18), dp(12))
        row.setBackgroundColor(bg)
        row.setOnClickListener { onClick(row) }
        val t1 = TextView(this)
        t1.text = title
        t1.textSize = 16f
        t1.setTextColor(if (danger) Color.parseColor("#E53935") else textColor)
        row.addView(t1)
        if (sub.isNotEmpty()) row.addView(smallText(sub))
        return row
    }

    private fun switchRow(title: String, key: String, def: Boolean, onChange: (Boolean) -> Unit = {}): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(18), dp(10), dp(18), dp(10))
        row.setBackgroundColor(bg)
        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(textColor)
        tv.textSize = 16f
        val sw = Switch(this)
        sw.isChecked = prefs.getBoolean(key, def)
        sw.setOnCheckedChangeListener { _, checked ->
            setPref(key, checked)
            onChange(checked)
        }
        row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(sw)
        return row
    }

    private fun seekRow(title: String, key: String, min: Int, max: Int, def: Int): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(12), dp(18), dp(12))
        col.setBackgroundColor(bg)
        val value = prefs.getInt(key, def)
        val caption = TextView(this)
        caption.text = "$title: $value"
        caption.setTextColor(textColor)
        caption.textSize = 15f
        val seek = SeekBar(this)
        seek.max = max - min
        seek.progress = value - min
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                caption.text = "$title: ${progress + min}"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                setInt(key, seek.progress + min)
            }
        })
        col.addView(caption)
        col.addView(seek)
        return col
    }

    private fun choiceRow(title: String, key: String, options: List<String>): LinearLayout {
        val current = options.getOrElse(prefs.getInt(key, 0)) { options[0] }
        return navRow(title, current) { v ->
            popup(v, options.mapIndexed { i, o -> o to { setInt(key, i); go(Screen.PRIVACY) } })
        }
    }

    private fun swatchRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(18), dp(10), dp(18), dp(10))
        row.setBackgroundColor(bg)
        val current = prefs.getInt("wall", 0)
        for (i in walls.indices) {
            val v = View(this)
            val shape = GradientDrawable()
            shape.cornerRadius = dp(8).toFloat()
            shape.setColor(if (i == 0) bg else Color.parseColor(walls[i]))
            shape.setStroke(if (i == current) dp(3) else dp(1), if (i == current) Color.parseColor("#3390EC") else subColor)
            v.background = shape
            v.setOnClickListener { setInt("wall", i); go(Screen.CHAT_SETTINGS) }
            row.addView(v, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        return row
    }

    // Всплывающее скруглённое меню под нажатым элементом
    private fun popup(anchor: View, items: List<Pair<String, () -> Unit>>, dangerIdx: Int = -1) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(6), 0, dp(6))
        val shape = GradientDrawable()
        shape.cornerRadius = dp(16).toFloat()
        shape.setColor(popupBg)
        box.background = shape

        val pw = PopupWindow(box, dp(240), LinearLayout.LayoutParams.WRAP_CONTENT, true)
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pw.isOutsideTouchable = true
        pw.elevation = dp(10).toFloat()

        items.forEachIndexed { i, item ->
            val tv = TextView(this)
            tv.text = item.first
            tv.textSize = 16f
            tv.setTextColor(if (i == dangerIdx) Color.parseColor("#E53935") else textColor)
            tv.setPadding(dp(18), dp(12), dp(18), dp(12))
            tv.setOnClickListener { pw.dismiss(); item.second() }
            box.addView(tv)
        }
        pw.showAsDropDown(anchor, dp(8), -dp(4))
        box.alpha = 0f
        box.scaleY = 0.9f
        box.animate().alpha(1f).scaleY(1f).setDuration(160).start()
    }

    // ---------- Вход по паролю (4 точки и цифровая панель) ----------
    private fun pinScreen(title: String, backTo: Screen?, onDone: (String, () -> Unit) -> Unit) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(bg)
        root.setPadding(dp(24), dp(24), dp(24), dp(24))

        if (backTo != null) {
            val back = Button(this)
            back.text = "←"
            back.setTextColor(textColor)
            back.setBackgroundColor(Color.TRANSPARENT)
            back.setOnClickListener { go(backTo) }
            root.addView(back, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.START })
        }

        val head = TextView(this)
        head.text = title
        head.setTextColor(textColor)
        head.textSize = 20f
        head.gravity = Gravity.CENTER
        root.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(32) })

        val dots = List(4) {
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { setMargins(dp(8), 0, dp(8), 0) }
            }
        }
        val dotRow = LinearLayout(this)
        dotRow.gravity = Gravity.CENTER
        dots.forEach { dotRow.addView(it) }
        root.addView(dotRow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(36); bottomMargin = dp(36) })

        var buf = ""
        fun paint() {
            dots.forEachIndexed { i, v ->
                val d = GradientDrawable()
                d.setShape(GradientDrawable.OVAL)
                d.setColor(if (i < buf.length) textColor else Color.TRANSPARENT)
                d.setStroke(dp(2), subColor)
                v.background = d
            }
        }
        paint()

        val fail: () -> Unit = {
            root.animate().translationX(dp(14).toFloat()).setDuration(50).withEndAction {
                root.animate().translationX(-dp(14).toFloat()).setDuration(50).withEndAction {
                    root.animate().translationX(0f).setDuration(50).start()
                }.start()
            }.start()
            buf = ""
            paint()
        }

        val pad = LinearLayout(this)
        pad.orientation = LinearLayout.VERTICAL
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))) {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER
            for (k in row) {
                val key = TextView(this)
                key.text = k
                key.textSize = 26f
                key.setTextColor(textColor)
                key.gravity = Gravity.CENTER
                key.layoutParams = LinearLayout.LayoutParams(dp(84), dp(72))
                if (k.isNotEmpty()) {
                    key.setOnClickListener {
                        if (k == "⌫") {
                            if (buf.isNotEmpty()) { buf = buf.dropLast(1); paint() }
                        } else if (buf.length < 4) {
                            buf += k
                            paint()
                            if (buf.length == 4) {
                                val code = buf
                                root.postDelayed({ onDone(code, fail) }, 150)
                            }
                        }
                    }
                }
                r.addView(key)
            }
            pad.addView(r)
        }
        root.addView(pad, LinearLayout.LayoutParams(-2, -2))
        setContentView(root)
    }

    private fun showLock() {
        pinScreen(t("locked_title"), null) { code, fail ->
            if (code == (prefs.getString("pin", "") ?: "")) {
                unlocked = true
                render()
                if (username.isEmpty()) go(Screen.PROFILE)
            } else {
                toast(t("wrong_pin"))
                fail()
            }
        }
    }

    private fun changePin() {
        pinScreen(t("set_pin"), Screen.PRIVACY) { first, _ ->
            pinScreen(t("repeat_pin"), Screen.PRIVACY) { second, fail ->
                if (first == second) {
                    setStr("pin", second)
                    go(Screen.PRIVACY)
                } else {
                    toast(t("pin_mismatch"))
                    fail()
                }
            }
        }
    }

    private fun disablePin() {
        pinScreen(t("enter_current"), Screen.PRIVACY) { code, fail ->
            if (code == (prefs.getString("pin", "") ?: "")) {
                setStr("pin", "")
                go(Screen.PRIVACY)
            } else {
                toast(t("wrong_pin"))
                fail()
            }
        }
    }

    // ---------- Список чатов ----------
    private fun buildChats() {
        val left = if (archiveView) Btn("←", Color.TRANSPARENT) { archiveView = false; go(Screen.CHATS) }
                   else Btn("☰", Color.TRANSPARENT) { openDrawer() }
        val right = Btn("🔍", Color.TRANSPARENT) { go(Screen.SEARCH) }
        page(if (archiveView) t("archive") else "BLEmess", left = left, right = right) { c ->
            if (!archiveView) c.addView(folderChips())
            val list = LinearLayout(this)
            list.orientation = LinearLayout.VERTICAL
            listBox = list
            c.addView(list)
            fillChatList()
        }
    }

    private fun folderChips(): HorizontalScrollView {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(10), dp(8), dp(10), dp(8))
        row.addView(chip(t("chat_all"), folderFilter == null) { folderFilter = null; go(Screen.CHATS) })
        for (f in folderMap().keys) {
            row.addView(chip(f, folderFilter == f) { folderFilter = f; go(Screen.CHATS) })
        }
        row.addView(chip("+", false) { go(Screen.FOLDERS) })
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(row)
        return scroll
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 14f
        tv.setTextColor(if (selected) Color.WHITE else textColor)
        tv.setPadding(dp(14), dp(7), dp(14), dp(7))
        val shape = GradientDrawable()
        shape.cornerRadius = dp(18).toFloat()
        shape.setColor(if (selected) Color.parseColor("#3390EC") else chipOff)
        tv.background = shape
        tv.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) }
        tv.setOnClickListener { onClick() }
        return tv
    }

    private fun fillChatList() {
        headerSub?.text = "${t("people_nearby")}: ${nearby.values.distinct().size}"
        val box = listBox ?: return
        box.removeAllViews()
        val pins = pinned()
        val arch = archived()
        val fmap = folderMap()
        val ff = folderFilter
        val peers = chats.keys.reversed()
            .filter { chats[it].orEmpty().isNotEmpty() }
            .filter { (it in arch) == archiveView }
            .filter { ff == null || fmap[ff]?.contains(it) == true }
            .sortedByDescending { it in pins }

        if (peers.isEmpty()) {
            box.addView(emptyText(if (archiveView) t("archive_empty") else t("no_chats")))
            return
        }

        for (peer in peers) {
            val msgs = chats[peer].orEmpty()
            val last = msgs.last()
            val shown = if (last.text.isEmpty() && last.img.isNotEmpty()) "📷 ${t("photo")}" else last.text
            val prefix = if (last.mine) "${t("you")}: " else ""
            val row = personRow(peer, prefix + shown, online(peer), msgs.count { it.unread }, peer in pins) { openChat(peer) }
            row.setOnLongClickListener { chatPopup(row, peer); true }
            box.addView(row, LinearLayout.LayoutParams(-1, -2))
            addSeparator(box)
        }
    }

    private fun personRow(name: String, sub: String, isOnline: Boolean, badge: Int, pinnedRow: Boolean, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(10), dp(16), dp(10))
        row.setBackgroundColor(if (pinnedRow) pinBg else bg)
        row.setOnClickListener { onClick() }

        val frame = FrameLayout(this)
        frame.addView(avatar(name), FrameLayout.LayoutParams(dp(50), dp(50)))
        if (isOnline) frame.addView(dot(), FrameLayout.LayoutParams(dp(14), dp(14), Gravity.BOTTOM or Gravity.END))
        row.addView(frame, LinearLayout.LayoutParams(dp(50), dp(50)).apply { rightMargin = dp(12) })

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val nameView = TextView(this)
        nameView.text = "@$name"
        nameView.setTextColor(textColor)
        nameView.textSize = 17f
        nameView.typeface = Typeface.DEFAULT_BOLD
        val subView = TextView(this)
        subView.text = sub
        subView.setTextColor(subColor)
        subView.textSize = 14f
        subView.maxLines = 1
        subView.ellipsize = TextUtils.TruncateAt.END
        texts.addView(nameView)
        texts.addView(subView)
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        if (pinnedRow) {
            val pin = TextView(this)
            pin.text = "📌"
            pin.textSize = 14f
            row.addView(pin)
        }
        if (badge > 0) {
            val b = TextView(this)
            b.text = badge.toString()
            b.setTextColor(Color.WHITE)
            b.textSize = 12f
            b.gravity = Gravity.CENTER
            b.setPadding(dp(7), dp(2), dp(7), dp(2))
            val shape = GradientDrawable()
            shape.cornerRadius = dp(20).toFloat()
            shape.setColor(Color.parseColor("#3390EC"))
            b.background = shape
            row.addView(b)
        }
        return row
    }

    private fun chatPopup(anchor: View, peer: String) {
        val items = listOf(
            (if (peer in pinned()) t("unpin") else t("pin")) to { togglePin(peer) },
            (if (peer in archived()) t("from_archive") else t("to_archive")) to { toggleArchive(peer) },
            t("to_folder") to { folderPopup(anchor, peer) },
            t("delete") to { deleteChat(peer) }
        )
        popup(anchor, items, 3)
    }

    private fun folderPopup(anchor: View, peer: String) {
        val fmap = folderMap()
        if (fmap.isEmpty()) { toast(t("no_folders")); return }
        val items = fmap.keys.map { f ->
            ((if (fmap[f]?.contains(peer) == true) "✓ " else "") + f) to {
                val set = fmap[f] ?: mutableSetOf()
                if (!set.add(peer)) set.remove(peer)
                fmap[f] = set
                saveFolderMap(fmap)
                refreshLive()
            }
        }
        popup(anchor, items)
    }

    private fun togglePin(peer: String) {
        val s = pinned()
        if (!s.add(peer)) s.remove(peer)
        saveSet("pinned", s)
        refreshLive()
    }

    private fun toggleArchive(peer: String) {
        val s = archived()
        if (!s.add(peer)) s.remove(peer)
        saveSet("archived", s)
        refreshLive()
    }

    private fun deleteChat(peer: String) {
        chats.remove(peer)
        refreshLive()
    }

    // ---------- Чат ----------
    private fun openChat(peer: String) {
        openPeer = peer
        if (!chats.containsKey(peer)) chats[peer] = mutableListOf()
        go(Screen.CHAT)
    }

    private fun buildChat() {
        val root = screenRoot()
        root.setBackgroundColor(chatBg)
        root.addView(titleBar("@$openPeer", "", backBtn(Screen.CHATS), null))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(12), dp(12), dp(12))
        msgBox = box

        val scroll = ScrollView(this)
        chatScroll = scroll
        scroll.addView(box)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(buildHintBar())
        root.addView(inputRow())
        show(root)
        fillMessages()
        fillHint()
    }

    private fun buildHintBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(barColor)
        bar.setPadding(dp(12), dp(4), dp(4), dp(4))
        bar.visibility = View.GONE

        val tv = TextView(this)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 13f
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        bar.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))

        val close = Button(this)
        close.text = "✕"
        close.setTextColor(Color.WHITE)
        close.setBackgroundColor(Color.TRANSPARENT)
        close.setOnClickListener { editingId = null; replyPreview = ""; fillHint() }
        bar.addView(close)

        hintBar = bar
        hintText = tv
        return bar
    }

    private fun fillHint() {
        val bar = hintBar ?: return
        val tv = hintText ?: return
        when {
            editingId != null -> { bar.visibility = View.VISIBLE; tv.text = t("editing") }
            replyPreview.isNotEmpty() -> { bar.visibility = View.VISIBLE; tv.text = "${t("reply_label")}: $replyPreview" }
            else -> { bar.visibility = View.GONE }
        }
    }

    private fun inputRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), dp(8), dp(8), dp(8))
        row.setBackgroundColor(barColor)

        val photo = Button(this)
        photo.text = "📷"
        photo.setBackgroundColor(Color.TRANSPARENT)
        photo.setOnClickListener { pickPhoto() }

        val input = EditText(this)
        input.hint = t("message_hint")
        input.setTextColor(textColor)
        input.setHintTextColor(subColor)
        input.setBackgroundColor(inputBg)
        input.textSize = fontSize.toFloat()
        input.setPadding(dp(14), dp(10), dp(14), dp(10))
        inputField = input

        val send = Button(this)
        send.text = "➤"
        send.setTextColor(Color.WHITE)
        send.isAllCaps = false
        send.setBackgroundColor(Color.parseColor("#3390EC"))
        send.setOnClickListener { submit() }

        row.addView(photo)
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(send)
        return row
    }

    private fun submit() {
        val field = inputField ?: return
        val text = field.text.toString().trim()
        if (text.isEmpty()) return
        val editing = editingId
        if (editing != null) {
            editMessage(openPeer, editing, text)
        } else {
            sendMessage(openPeer, text, replyPreview, "")
        }
        field.setText("")
        editingId = null
        replyPreview = ""
        fillMessages()
        fillHint()
    }

    private fun fillMessages() {
        headerSub?.text = if (online(openPeer)) t("online") else t("offline")
        val box = msgBox ?: return
        box.removeAllViews()
        for (m in chats[openPeer].orEmpty()) {
            box.addView(bubble(m, openPeer), LinearLayout.LayoutParams(-1, -2))
        }
        markRead(openPeer)
        chatScroll?.post { chatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun markRead(peer: String) {
        val ids = JSONArray()
        for (m in chats[peer].orEmpty()) {
            if (m.unread) {
                m.unread = false
                ids.put(m.id)
            }
        }
        if (ids.length() > 0) sendPacket(newPacket("read", peer).put("ids", ids))
    }

    private fun smallLine(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = 12f
        tv.setTextColor(subColor)
        return tv
    }

    private fun bubble(m: Msg, peer: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = if (m.mine) Gravity.END else Gravity.START
        row.setPadding(0, dp(3), 0, dp(3))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(10), dp(8), dp(10), dp(6))
        val shape = GradientDrawable()
        shape.cornerRadius = dp(16).toFloat()
        shape.setColor(if (m.mine) myBubble else otherBubble)
        box.background = shape

        if (m.fwd.isNotEmpty()) box.addView(smallLine("${t("forwarded_from")} @${m.fwd}"))
        if (m.reply.isNotEmpty()) box.addView(smallLine("↪ ${m.reply}"))

        if (m.img.isNotEmpty()) {
            val iv = ImageView(this)
            iv.setImageBitmap(BitmapFactory.decodeFile(m.img, BitmapFactory.Options().apply { inSampleSize = 2 }))
            iv.adjustViewBounds = true
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.maxWidth = dp(240)
            iv.maxHeight = dp(320)
            box.addView(iv, LinearLayout.LayoutParams(-2, -2))
        }

        if (m.text.isNotEmpty()) {
            val body = TextView(this)
            body.text = if (m.edited) "${m.text} ${t("edited")}" else m.text
            body.setTextColor(textColor)
            body.textSize = fontSize.toFloat()
            box.addView(body)
        }

        if (m.mine) {
            val meta = TextView(this)
            meta.text = if (m.read) "✓✓" else "✓"
            meta.setTextColor(if (m.read) Color.parseColor("#4FC3F7") else subColor)
            meta.textSize = 11f
            meta.gravity = Gravity.END
            box.addView(meta, LinearLayout.LayoutParams(-1, -2))
        }

        row.addView(box, LinearLayout.LayoutParams(-2, -2))
        box.setOnLongClickListener { messagePopup(box, peer, m); true }
        return row
    }

    private fun previewOf(m: Msg): String =
        "@${m.from}: " + (if (m.text.isNotEmpty()) m.text.take(60) else t("photo"))

    private fun messagePopup(anchor: View, peer: String, m: Msg) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items.add(t("reply") to { replyPreview = previewOf(m); editingId = null; fillHint() })
        items.add(t("forward") to { pickForward(anchor, m) })
        if (m.text.isNotEmpty()) items.add(t("copy") to { copyText(m.text) })
        if (m.mine && m.img.isEmpty()) items.add(t("edit") to { startEdit(m) })
        items.add(t("delete") to { deletePopup(anchor, peer, m) })
        popup(anchor, items)
    }

    private fun startEdit(m: Msg) {
        editingId = m.id
        replyPreview = ""
        inputField?.setText(m.text)
        fillHint()
    }

    private fun deletePopup(anchor: View, peer: String, m: Msg) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items.add(t("delete_for_me") to { deleteLocal(peer, m.id) })
        if (m.mine) items.add(t("delete_for_both") to { deleteBoth(peer, m) })
        popup(anchor, items)
    }

    private fun deleteLocal(peer: String, id: String) {
        chats[peer]?.removeAll { it.id == id }
        refreshLive()
    }

    private fun deleteBoth(peer: String, m: Msg) {
        deleteLocal(peer, m.id)
        sendPacket(newPacket("del", peer).put("target", m.id))
    }

    private fun pickForward(anchor: View, m: Msg) {
        val targets = chats.keys.toList()
        if (targets.isEmpty()) { toast(t("no_chats_forward")); return }
        popup(anchor, targets.map { peer -> ("@$peer") to { forwardMsg(peer, m) } })
    }

    private fun forwardMsg(peer: String, m: Msg) {
        val from = if (m.mine) myName else m.from
        if (m.img.isNotEmpty()) sendPhotoFile(peer, File(m.img), from, "")
        else sendMessage(peer, m.text, "", from)
        toast(t("forwarded"))
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("message", text))
        toast(t("copied"))
    }

    private fun editMessage(peer: String, id: String, text: String) {
        val m = chats[peer]?.firstOrNull { it.id == id } ?: return
        if (!m.mine) return
        m.text = text
        m.edited = true
        sendPacket(newPacket("edit", peer).put("target", id).put("text", text))
        refreshLive()
    }

    // ---------- Фото ----------
    private fun pickPhoto() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, 43)
    }

    private fun sendPhotoFromUri(uri: Uri) {
        val peer = openPeer
        Thread {
            try {
                val file = copyToLocalImage(uri)
                if (file != null) ui {
                    val reply = replyPreview
                    replyPreview = ""
                    editingId = null
                    sendPhotoFile(peer, file, "", reply)
                    fillHint()
                }
            } catch (e: Exception) {
                ui { toast(t("net_err")) }
            }
        }.start()
    }

    // Сжимаем картинку до 1024 px, чтобы её быстро передавать
    private fun copyToLocalImage(uri: Uri): File? {
        val original = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scale = 1024f / maxOf(original.width, original.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
        } else original
        val dir = File(filesDir, "img").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + ".jpg")
        FileOutputStream(file).use { small.compress(Bitmap.CompressFormat.JPEG, 75, it) }
        return file
    }

    private fun sendPhotoFile(peer: String, file: File, fwd: String, reply: String) {
        try {
            val fp = Payload.fromFile(file)
            val p = newPacket("photo", peer)
                .put("fileId", fp.id)
                .put("reply", reply)
                .put("fwd", fwd)
            addMsg(peer, Msg(id = p.getString("id"), from = myName, text = "", mine = true,
                reply = reply, fwd = fwd, img = file.absolutePath))
            sendPacket(p)
            val targets = links.keys.toList()
            if (targets.isNotEmpty()) client.sendPayload(targets, fp)
            refreshLive()
        } catch (e: Exception) {
            toast(t("net_err"))
        }
    }

    // ---------- Профиль ----------
    private fun buildProfile() {
        page(t("profile"), sub = t("profile_sub")) { c ->
            val big = avatar(myName)
            big.textSize = 36f
            c.addView(big, LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(20)
            })

            val nameTv = TextView(this)
            nameTv.text = "@$myName"
            nameTv.setTextColor(textColor)
            nameTv.textSize = 20f
            nameTv.typeface = Typeface.DEFAULT_BOLD
            nameTv.gravity = Gravity.CENTER
            c.addView(nameTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

            c.addView(label(t("username_label")))
            val nameIn = EditText(this)
            nameIn.hint = "ivan_07"
            nameIn.setText(username)
            nameIn.setTextColor(textColor)
            nameIn.setHintTextColor(subColor)
            c.addView(nameIn, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(18); rightMargin = dp(18) })

            val save = Button(this)
            save.text = t("save")
            save.setTextColor(Color.WHITE)
            save.isAllCaps = false
            save.setBackgroundColor(Color.parseColor("#3390EC"))
            save.setOnClickListener {
                val name = nameIn.text.toString().trim().removePrefix("@")
                if (!name.matches(Regex("[A-Za-z0-9_]{3,32}"))) {
                    toast(t("username_err"))
                    return@setOnClickListener
                }
                username = name
                archiveView = false
                startMesh()
                go(Screen.CHATS)
            }
            c.addView(save, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(18); rightMargin = dp(18); topMargin = dp(16)
            })
        }
    }

    // ---------- Поиск ----------
    private fun buildSearch() {
        page(t("search_title"), left = backBtn(Screen.CHATS)) { c ->
            val find = EditText(this)
            find.hint = t("search_hint")
            find.setTextColor(textColor)
            find.setHintTextColor(subColor)
            c.addView(find, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(12)
            })

            val findBtn = Button(this)
            findBtn.text = t("find")
            findBtn.setTextColor(Color.WHITE)
            findBtn.isAllCaps = false
            findBtn.setBackgroundColor(Color.parseColor("#F44336"))
            findBtn.setOnClickListener {
                val q = find.text.toString().trim().removePrefix("@")
                if (q.isEmpty()) return@setOnClickListener
                val found = (nearby.values + chats.keys).firstOrNull { it.equals(q, ignoreCase = true) }
                if (found != null) openChat(found) else toast("@$q ${t("not_found")}")
            }
            c.addView(findBtn, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8)
            })

            c.addView(label(t("nearby_now")))
            val list = LinearLayout(this)
            list.orientation = LinearLayout.VERTICAL
            nearbyBox = list
            c.addView(list)
            fillNearby()
        }
    }

    private fun fillNearby() {
        val box = nearbyBox ?: return
        box.removeAllViews()
        val names = nearby.values.distinct()
        headerSub?.text = "${t("people_nearby")}: ${names.size}"
        if (names.isEmpty()) {
            box.addView(emptyText(t("nobody_nearby")))
            return
        }
        for (name in names) {
            val isOn = online(name)
            box.addView(
                personRow(name, if (isOn) t("online") else t("nearby_word"), isOn, 0, false) { openChat(name) },
                LinearLayout.LayoutParams(-1, -2)
            )
            addSeparator(box)
        }
    }

    // ---------- Настройки ----------
    private fun buildSettings() {
        page(t("settings")) { c ->
            addRow(c, navRow(t("account")) { go(Screen.ACCOUNT) })
            addRow(c, navRow(t("chat_settings")) { go(Screen.CHAT_SETTINGS) })
            addRow(c, navRow(t("privacy")) { go(Screen.PRIVACY) })
            addRow(c, navRow(t("notifications")) { go(Screen.NOTIFY) })
            addRow(c, navRow(t("folders")) { go(Screen.FOLDERS) })
            addRow(c, navRow(t("language"), LANGS.getOrElse(lang) { LANGS[0] }) { go(Screen.LANGUAGE) })
        }
    }

    private fun buildAccount() {
        page(t("account"), left = backBtn(Screen.SETTINGS)) { c ->
            addRow(c, navRow(t("username_short"), "@${username.ifEmpty { t("not_set") }}") { go(Screen.PROFILE) })
            addRow(c, navRow(t("delete_account"), t("delete_account_msg"), danger = true) { v ->
                popup(v, listOf(t("delete_account") to { deleteAccount() }), 0)
            })
        }
    }

    private fun deleteAccount() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        links.clear(); nearby.clear(); pending.clear(); seen.clear(); chats.clear()
        metaByFile.clear(); fileByFile.clear()
        File(filesDir, "img").deleteRecursively()
        prefs.edit().clear().apply()
        myId = newId()
        dark = false
        unlocked = true
        archiveView = false
        folderFilter = null
        go(Screen.PROFILE)
    }

    private fun buildChatSettings() {
        page(t("chat_settings"), left = backBtn(Screen.SETTINGS)) { c ->
            c.addView(label(t("wallpaper")))
            addRow(c, swatchRow())
            addRow(c, seekRow(t("text_size"), "fontSize", 12, 24, 16))
            addRow(c, switchRow(t("dark_theme"), "dark", dark) { on ->
                dark = on
                go(Screen.CHAT_SETTINGS)
            })
            addRow(c, navRow(t("app_icon")) { toast(t("unavailable")) })
        }
    }

    private fun buildPrivacy() {
        page(t("privacy"), left = backBtn(Screen.SETTINGS)) { c ->
            val pinOn = (prefs.getString("pin", "") ?: "").isNotEmpty()
            addRow(c, navRow(t("passcode"), if (pinOn) t("on") else t("off")) {
                if (pinOn) disablePin() else changePin()
            })
            addRow(c, navRow(t("blacklist"), "${t("blocked_count")}: ${blocked().size}") { go(Screen.BLACKLIST) })
            addRow(c, choiceRow(t("last_seen"), "seenBy", listOf(t("everyone"), t("nobody"), t("contacts_only"))))
            addRow(c, choiceRow(t("fwd_privacy"), "forwardBy", listOf(t("everyone"), t("nobody"))))
            addRow(c, choiceRow(t("who_writes"), "whoWrites", listOf(t("nearby_only"), t("contacts_only"))))
        }
    }

    private fun buildBlacklist() {
        page(t("blacklist"), left = backBtn(Screen.PRIVACY)) { c ->
            val input = EditText(this)
            input.hint = "@"
            input.setTextColor(textColor)
            input.setHintTextColor(subColor)
            c.addView(input, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(12)
            })

            val addBtn = Button(this)
            addBtn.text = t("block")
            addBtn.setTextColor(Color.WHITE)
            addBtn.isAllCaps = false
            addBtn.setBackgroundColor(Color.parseColor("#F44336"))
            addBtn.setOnClickListener {
                val name = input.text.toString().trim().removePrefix("@")
                if (name.isNotEmpty()) {
                    val s = blocked()
                    s.add(name)
                    saveSet("blacklist", s)
                    go(Screen.BLACKLIST)
                }
            }
            c.addView(addBtn, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8)
            })

            val list = blocked().sorted()
            if (list.isEmpty()) c.addView(emptyText(t("list_empty")))
            for (name in list) {
                addRow(c, navRow("@$name", t("unblock_hint")) {
                    val s = blocked()
                    s.remove(name)
                    saveSet("blacklist", s)
                    go(Screen.BLACKLIST)
                })
            }
        }
    }

    private fun buildNotify() {
        page(t("notifications"), left = backBtn(Screen.SETTINGS)) { c ->
            addRow(c, seekRow(t("volume"), "volume", 0, 100, 80))
            addRow(c, switchRow(t("notif_sound"), "notifSound", true))
            val custom = (prefs.getString("sound", "") ?: "").isNotEmpty()
            addRow(c, navRow(t("tone"), if (custom) t("custom_word") else t("default_word")) { pickRingtone() })
        }
    }

    private fun pickRingtone() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        startActivityForResult(intent, 42)
    }

    private fun buildLanguage() {
        page(t("language"), left = backBtn(Screen.SETTINGS)) { c ->
            LANGS.forEachIndexed { i, name ->
                addRow(c, navRow(name, if (i == lang) "✓" else "") {
                    setInt("langIdx", i)
                    go(Screen.LANGUAGE)
                })
            }
        }
    }

    private fun buildFolders() {
        page(t("folders"), left = backBtn(Screen.SETTINGS)) { c ->
            addRow(c, navRow(t("chat_all")) { archiveView = false; folderFilter = null; go(Screen.CHATS) })
            addRow(c, navRow(t("archive")) { archiveView = true; go(Screen.CHATS) })

            c.addView(label(t("own_folders")))
            val input = EditText(this)
            input.hint = t("folder_name")
            input.setTextColor(textColor)
            input.setHintTextColor(subColor)
            c.addView(input, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(16); rightMargin = dp(16) })

            val create = Button(this)
            create.text = t("folder_new")
            create.setTextColor(Color.WHITE)
            create.isAllCaps = false
            create.setBackgroundColor(Color.parseColor("#3390EC"))
            create.setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val fmap = folderMap()
                    if (!fmap.containsKey(name)) {
                        fmap[name] = mutableSetOf()
                        saveFolderMap(fmap)
                    }
                    go(Screen.FOLDERS)
                }
            }
            c.addView(create, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8)
            })

            for (f in folderMap().keys) {
                addRow(c, navRow(f, t("folder_tap_delete")) { v ->
                    popup(v, listOf(t("delete") to { deleteFolder(f) }), 0)
                })
            }
        }
    }

    private fun deleteFolder(name: String) {
        val fmap = folderMap()
        fmap.remove(name)
        saveFolderMap(fmap)
        if (folderFilter == name) folderFilter = null
        go(Screen.FOLDERS)
    }

    // ---------- Настройки в памяти телефона ----------
    private fun setPref(key: String, v: Boolean) { prefs.edit().putBoolean(key, v).apply() }
    private fun setInt(key: String, v: Int) { prefs.edit().putInt(key, v).apply() }
    private fun setStr(key: String, v: String) { prefs.edit().putString(key, v).apply() }
    private fun loadSet(key: String): MutableSet<String> =
        (prefs.getString(key, "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableSet()
    private fun saveSet(key: String, s: Set<String>) { prefs.edit().putString(key, s.joinToString(",")).apply() }
    private fun pinned() = loadSet("pinned")
    private fun archived() = loadSet("archived")
    private fun blocked() = loadSet("blacklist")
    private fun online(name: String) = links.values.contains(name)

    private fun folderMap(): LinkedHashMap<String, MutableSet<String>> {
        val map = LinkedHashMap<String, MutableSet<String>>()
        val raw = prefs.getString("fm", "") ?: ""
        if (raw.isEmpty()) return map
        val o = JSONObject(raw)
        o.keys().forEach { k ->
            val arr = o.getJSONArray(k)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            map[k] = set
        }
        return map
    }

    private fun saveFolderMap(map: Map<String, Set<String>>) {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, JSONArray(v.toList()))
        prefs.edit().putString("fm", o.toString()).apply()
    }

    // ---------- Сохранение переписки ----------
    private fun msgToJson(m: Msg): JSONObject = JSONObject()
        .put("id", m.id).put("from", m.from).put("text", m.text).put("mine", m.mine)
        .put("read", m.read).put("edited", m.edited).put("reply", m.reply)
        .put("fwd", m.fwd).put("unread", m.unread).put("img", m.img)

    private fun msgFromJson(o: JSONObject): Msg = Msg(
        id = o.getString("id"), from = o.getString("from"), text = o.getString("text"),
        mine = o.getBoolean("mine"), read = o.optBoolean("read"), edited = o.optBoolean("edited"),
        reply = o.optString("reply"), fwd = o.optString("fwd"), unread = o.optBoolean("unread"),
        img = o.optString("img")
    )

    private fun saveChats() {
        val arr = JSONArray()
        for ((peer, list) in chats) {
            val msgs = JSONArray()
            list.forEach { msgs.put(msgToJson(it)) }
            arr.put(JSONObject().put("peer", peer).put("msgs", msgs))
        }
        prefs.edit().putString("chats", arr.toString()).apply()
    }

    private fun loadChats() {
        val raw = prefs.getString("chats", "") ?: ""
        if (raw.isEmpty()) return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val msgs = o.getJSONArray("msgs")
                val list = mutableListOf<Msg>()
                for (j in 0 until msgs.length()) list.add(msgFromJson(msgs.getJSONObject(j)))
                chats[o.getString("peer")] = list
            }
        } catch (e: Exception) {
            // повреждённые данные пропускаем
        }
    }

    // ---------- Сеть (Nearby Connections) ----------
    private fun startMesh() {
        client.stopAdvertising()
        client.stopDiscovery()
        val options = Strategy.P2P_CLUSTER
        client.startAdvertising(
            myName, serviceId, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(options).build()
        ).addOnFailureListener { toast(t("net_err")) }
        client.startDiscovery(
            serviceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(options).build()
        ).addOnFailureListener { toast(t("net_err")) }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            ui {
                nearby[endpointId] = info.endpointName
                if (endpointId !in links.keys && endpointId !in pending) {
                    pending.add(endpointId)
                    client.requestConnection(myName, endpointId, connectionCallback)
                        .addOnFailureListener { pending.remove(endpointId) }
                }
                refreshLive()
            }
        }

        override fun onEndpointLost(endpointId: String) {
            ui {
                nearby.remove(endpointId)
                refreshLive()
            }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            ui { nearby[endpointId] = info.endpointName }
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            ui {
                pending.remove(endpointId)
                if (result.status.isSuccess) {
                    links[endpointId] = nearby[endpointId] ?: endpointId
                }
                refreshLive()
            }
        }

        override fun onDisconnected(endpointId: String) {
            ui {
                links.remove(endpointId)
                pending.remove(endpointId)
                refreshLive()
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.FILE) {
                val file = payload.asFile()?.asJavaFile() ?: return
                val fid = payload.id
                ui {
                    fileByFile[fid] = Pair(endpointId, file)
                    tryPhoto(fid)
                }
                return
            }
            val bytes = payload.asBytes() ?: return
            ui {
                try {
                    handleIncoming(endpointId, JSONObject(String(bytes)))
                } catch (e: Exception) {
                    // не удалось разобрать пакет
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    // ---------- Пакеты ----------
    private fun newPacket(type: String, to: String): JSONObject = JSONObject()
        .put("id", UUID.randomUUID().toString())
        .put("type", type)
        .put("from", myName)
        .put("to", to)
        .put("ttl", 6)

    private fun sendPacket(p: JSONObject) {
        seen.add(p.getString("id"))
        forward(p, null)
    }

    private fun sendMessage(peer: String, text: String, reply: String, fwd: String) {
        val p = newPacket("msg", peer)
            .put("text", text)
            .put("reply", reply)
            .put("fwd", fwd)
        addMsg(peer, Msg(id = p.getString("id"), from = myName, text = text, mine = true, reply = reply, fwd = fwd))
        sendPacket(p)
        refreshLive()
    }

    private fun receiveMessage(p: JSONObject, from: String) {
        val msg = Msg(
            id = p.getString("id"), from = from, text = p.getString("text"), mine = false,
            reply = p.optString("reply"), fwd = p.optString("fwd"), unread = true
        )
        addMsg(from, msg)
        if (!(screen == Screen.CHAT && openPeer == from)) notify(from, msg.text)
    }

    // Переносим чат в конец, чтобы самые свежие были сверху
    private fun addMsg(peer: String, m: Msg) {
        val list = chats.remove(peer) ?: mutableListOf()
        list.add(m)
        chats[peer] = list
    }

    private fun handleIncoming(fromEndpoint: String, p: JSONObject) {
        val id = p.getString("id")
        if (!seen.add(id)) return
        val type = p.getString("type")

        if (type == "photo") {
            val fid = p.getLong("fileId")
            metaByFile[fid] = Pair(fromEndpoint, p)
            tryPhoto(fid)
            return
        }

        val from = p.getString("from")
        val to = p.getString("to")
        val ttl = p.getInt("ttl")

        if (to == myName && from !in blocked()) {
            when (type) {
                "msg" -> receiveMessage(p, from)
                "edit" -> {
                    val target = p.getString("target")
                    chats[from]?.firstOrNull { it.id == target }?.let {
                        it.text = p.getString("text")
                        it.edited = true
                    }
                }
                "del" -> {
                    val target = p.getString("target")
                    chats[from]?.removeAll { it.id == target }
                }
                "read" -> {
                    val ids = p.getJSONArray("ids")
                    for (i in 0 until ids.length()) {
                        val target = ids.getString(i)
                        chats[from]?.firstOrNull { it.id == target }?.read = true
                    }
                }
            }
            refreshLive()
        }

        if (ttl > 1) forward(JSONObject(p.toString()).put("ttl", ttl - 1), fromEndpoint)
    }

    // Фото приходит двумя частями: описание (пакет) и сам файл. Ждём обе и собираем.
    private fun tryPhoto(fid: Long) {
        val (ep, p) = metaByFile[fid] ?: return
        val (fep, file) = fileByFile[fid] ?: return
        metaByFile.remove(fid)
        fileByFile.remove(fid)
        try {
            val id = p.getString("id")
            val dir = File(filesDir, "img").apply { mkdirs() }
            val local = File(dir, "$id.jpg")
            file.copyTo(local, overwrite = true)

            val from = p.getString("from")
            val to = p.getString("to")
            val ttl = p.getInt("ttl")

            if (to == myName && from !in blocked()) {
                addMsg(from, Msg(
                    id = id, from = from, text = "", mine = false,
                    reply = p.optString("reply"), fwd = p.optString("fwd"),
                    unread = true, img = local.absolutePath
                ))
                refreshLive()
                if (!(screen == Screen.CHAT && openPeer == from)) notify(from, t("photo"))
            }

            if (ttl > 1) {
                val np = JSONObject(p.toString()).put("ttl", ttl - 1)
                val fp = Payload.fromFile(local)
                np.put("fileId", fp.id)
                forward(np, ep)
                val others = links.keys.filter { it != fep }
                if (others.isNotEmpty()) client.sendPayload(others, fp)
            }
        } catch (e: Exception) {
            // файл не собрался, пропускаем
        }
    }

    // Пересылка всем соседям, кроме того, от кого пришёл пакет
    private fun forward(p: JSONObject, except: String?) {
        val bytes = p.toString().toByteArray()
        for (ep in links.keys.toList()) {
            if (ep != except) client.sendPayload(ep, Payload.fromBytes(bytes))
        }
    }

    private fun notify(from: String, text: String) {
        toast("@$from: $text")
        if (!prefs.getBoolean("notifSound", true)) return
        val saved = prefs.getString("sound", "") ?: ""
        val uri = if (saved.isEmpty()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            Uri.parse(saved)
        }
        val r = RingtoneManager.getRingtone(this, uri) ?: return
        if (Build.VERSION.SDK_INT >= 28) r.volume = prefs.getInt("volume", 80) / 100f
        r.play()
    }
}
