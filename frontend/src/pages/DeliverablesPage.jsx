import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getMe } from '../api/auth'
import { downloadDeliverable, getDeliverables, uploadDeliverable } from '../api/deliverables'
import { getTeam, getTeamMembers } from '../api/teams'
import DeliverableList from '../components/DeliverableList'
import DeliverableUploadForm from '../components/DeliverableUploadForm'

// 조의 산출물 화면입니다. 목록과 다운로드는 조원과 교수가, 올리기는 역할이 배정된 조원만 합니다(서버가 확인합니다).
function DeliverablesPage() {
    const { teamId } = useParams()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [deliverables, setDeliverables] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    const [selectedFile, setSelectedFile] = useState(null)
    const [uploading, setUploading] = useState(false)
    // 올리기에 성공할 때마다 1씩 늘려 파일 칸을 비웁니다(DeliverableUploadForm의 inputKey).
    const [fileInputKey, setFileInputKey] = useState(0)
    const [downloadingId, setDownloadingId] = useState(null)

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId), getDeliverables(teamId)])
            .then(([meData, teamData, membersData, deliverablesData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                setDeliverables(deliverablesData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

    // 고른 파일을 올립니다. 성공하면 새 산출물을 목록 맨 위(최신)에 더합니다.
    function handleUpload() {
        if (!selectedFile) {
            setActionError('올릴 파일을 고르세요')
            return
        }
        setActionError(null)
        setUploading(true)
        uploadDeliverable(teamId, selectedFile)
            .then((created) => {
                setDeliverables([created, ...deliverables])
                setSelectedFile(null)
                setFileInputKey(fileInputKey + 1)
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setUploading(false))
    }

    // 파일을 받아 브라우저의 "다운로드"로 저장합니다.
    function handleDownload(deliverable) {
        setActionError(null)
        setDownloadingId(deliverable.id)
        downloadDeliverable(deliverable.id)
            .then((blob) => saveBlob(blob, deliverable.fileName))
            .catch((err) => setActionError(err.message))
            .finally(() => setDownloadingId(null))
    }

    if (loading) return <p>불러오는 중...</p>
    if (error) {
        return (
            <main>
                <p className="error">{error}</p>
                <p><Link to="/">과제 목록으로</Link></p>
            </main>
        )
    }

    // 내 조 참여 기록입니다. 교수는 조원이 아니라서 없습니다(undefined). 내 역할이 있을 때만 올리기 form을 보여 줍니다.
    const myTeamMember = members.find((member) => member.memberId === me.id)
    const canUpload = myTeamMember !== undefined && myTeamMember.roleName !== null

    return (
        <main>
            <p className="links"><Link to={`/teams/${team.id}`}>← {team.name}</Link></p>
            <h1>{team.name} 산출물</h1>

            {canUpload && (
                <DeliverableUploadForm
                    roleName={myTeamMember.roleName}
                    onFileChange={setSelectedFile}
                    onUpload={handleUpload}
                    uploading={uploading}
                    inputKey={fileInputKey}
                />
            )}
            {myTeamMember && myTeamMember.roleName === null && <p className="notice">역할이 배정되면 산출물을 올릴 수 있습니다.</p>}
            {actionError && <p className="error">{actionError}</p>}

            <h2>올린 산출물 (최신순)</h2>
            <DeliverableList deliverables={deliverables} onDownload={handleDownload} downloadingId={downloadingId} />
        </main>
    )
}

// Blob을 파일로 저장합니다. 임시 주소(object URL)를 만든 <a download>를 눌러 브라우저의 다운로드를 쓰고, 다 쓴 주소는 지웁니다.
function saveBlob(blob, fileName) {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    URL.revokeObjectURL(url)
}

export default DeliverablesPage
