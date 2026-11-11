import { formatShortDateTime, NOTIFICATION_TYPE_LABELS } from '../format'

// 받은 알림을 받은 순서대로 그립니다. 안 읽은 알림은 "새 알림" 꼬리표를 붙입니다. 개수는 세지 않습니다.
// 알림 문장을 누르면 onOpen(알림)을 부릅니다. 읽음 표시와 화면 이동은 page가 합니다.
// <ul>, <li>: 같은 모양이 이어지는 목록이라 목록 태그로 둡니다. 화면 읽기 프로그램이 목록으로 알아보고 한 번에 건너뛸 수 있습니다.
function NotificationList({ notifications, onOpen }) {
    if (notifications.length === 0) {
        return <div className="empty"><p className="sub">받은 알림이 없습니다.</p></div>
    }

    return (
        <ul>
            {notifications.map((notification) => (
                <li key={notification.id} className="log notice-row">
                    {notification.readAt === null
                        ? <span className="pill blue">새 알림</span>
                        : <span className="pill gray">읽음</span>}
                    <span className="log-text notice-text">
                        <span className="sub">{NOTIFICATION_TYPE_LABELS[notification.type]}</span>
                        <button type="button" className="notice-link" onClick={() => onOpen(notification)}>{notification.message}</button>
                    </span>
                    <time>{formatShortDateTime(notification.createdAt)}</time>
                </li>
            ))}
        </ul>
    )
}

export default NotificationList
