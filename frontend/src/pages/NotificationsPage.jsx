import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { getNotifications, readNotification } from '../api/notifications'
import NotificationList from '../components/NotificationList'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import TopBar from '../components/TopBar'
import { notificationPathOf } from '../format'

// 내가 받은 알림 화면입니다. 평가 시작, 기여 항목 담당 지정, 완료 보고 때 서버가 남긴 알림을 최근 것부터 보여 줍니다.
// 알림은 화면을 열 때 불러옵니다. 열어 둔 동안 새로 생긴 알림은 다시 들어와야 보입니다(실시간 전송 안 함).
function NotificationsPage() {
    const [notifications, setNotifications] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    const navigate = useNavigate()

    useEffect(() => {
        getNotifications()
            .then((data) => setNotifications(data))
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    // 알림을 읽음으로 표시한 뒤 그 알림의 화면으로 갑니다. 이미 읽은 알림은 표시 요청 없이 바로 갑니다.
    // 화면을 옮기면 공통 틀(AppLayout)이 알림을 다시 불러와 종 모양 버튼의 점을 고칩니다.
    function handleOpen(notification) {
        setActionError(null)
        const marking = notification.readAt === null ? readNotification(notification.id) : Promise.resolve(notification)
        marking
            .then(() => navigate(notificationPathOf(notification)))
            .catch((err) => setActionError(err.message))
    }

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    return (
        <>
            <TopBar crumbs={['알림']} />
            {actionError && <div className="errband" role="alert">{actionError}</div>}
            <main className="content">
                <div className="page-head">
                    <div>
                        <h1>알림</h1>
                        <p>평가 시작, 기여 항목 담당 지정, 완료 보고를 알려 줍니다. 알림을 누르면 읽음으로 표시하고 그 화면으로 갑니다.</p>
                    </div>
                </div>
                <section className="card">
                    <NotificationList notifications={notifications} onOpen={handleOpen} />
                </section>
            </main>
        </>
    )
}

export default NotificationsPage
