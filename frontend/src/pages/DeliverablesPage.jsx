import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getMe } from '../api/auth'
import { confirmDeliverable, getDeliverableConfirmations } from '../api/confirmations'
import { deleteDeliverable, downloadDeliverable, getDeliverables, uploadDeliverable } from '../api/deliverables'
import { getTeam, getTeamMembers } from '../api/teams'
import DeliverableList from '../components/DeliverableList'
import DeliverableUploadForm from '../components/DeliverableUploadForm'

// 조의 산출물 화면입니다. 목록과 다운로드, 완료확인 이력은 조원과 교수가, 올리기는 기록 단계에서 역할이 배정된 조원만, 삭제는 기록 단계에서 올린 조원만, 완료확인은 기록 단계에서 올린 조원을 뺀 조원이 합니다(서버가 확인합니다).
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
    // 삭제와 올리기는 서로 끝날 때까지 막습니다. 동시에 끝나면 나중에 끝난 쪽이 옛 deliverables로 덮어써 먼저 끝난 결과가 사라지기 때문입니다.
    const [deleting, setDeleting] = useState(false)
    // 완료확인 이력을 열어 둔 산출물의 번호입니다. 한 번에 하나만 열립니다.
    const [confirmationTargetId, setConfirmationTargetId] = useState(null)
    const [confirmations, setConfirmations] = useState([])
    // 이력 불러오기와 완료확인은 서로 끝날 때까지 막습니다. 다른 줄의 이력이 열어 둔 줄에 섞이지 않게 합니다.
    const [confirmationBusy, setConfirmationBusy] = useState(false)

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

    // 파일 본문을 되돌릴 수 없는 동작이라 한 번 더 묻고 삭제합니다. 성공하면 그 줄만 서버가 돌려준 값으로 바꿉니다. 목록에서 빼지 않습니다.
    function handleDelete(target) {
        if (!window.confirm(`${target.fileName} 파일을 삭제할까요? 파일은 되돌릴 수 없고, 올린 기록은 "삭제됨"으로 남습니다.`)) return
        setActionError(null)
        setDeleting(true)
        deleteDeliverable(target.id)
            .then((updated) => {
                setDeliverables(deliverables.map((deliverable) => (deliverable.id === updated.id ? updated : deliverable)))
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setDeleting(false))
    }

    // 완료확인 이력을 불러와 그 줄에 엽니다. 다른 줄의 이력이 열려 있었으면 바꿉니다.
    function handleOpenConfirmations(target) {
        setActionError(null)
        setConfirmationBusy(true)
        getDeliverableConfirmations(target.id)
            .then((data) => {
                setConfirmations(data)
                setConfirmationTargetId(target.id)
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setConfirmationBusy(false))
    }

    // 완료확인 이력을 닫습니다.
    function handleCloseConfirmations() {
        setConfirmationTargetId(null)
        setConfirmations([])
    }

    // 열어 둔 산출물을 확인합니다. 성공하면 새 확인을 이력 맨 위(최근)에 더합니다.
    function handleConfirm() {
        setActionError(null)
        setConfirmationBusy(true)
        confirmDeliverable(confirmationTargetId)
            .then((created) => setConfirmations([created, ...confirmations]))
            .catch((err) => setActionError(err.message))
            .finally(() => setConfirmationBusy(false))
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

    if (loading) return <main><p>불러오는 중...</p></main>
    if (error) {
        return (
            <main>
                <p className="error">{error}</p>
                <p><Link to="/">과제 목록으로</Link></p>
            </main>
        )
    }

    // 내 조 참여 기록입니다. 교수는 조원이 아니라서 없습니다(undefined). 기록 단계이고 내 역할이 있을 때만 올리기 form을 보여 줍니다.
    const myTeamMember = members.find((member) => member.memberId === me.id)
    const isRecording = team.phase === 'RECORDING'
    const canUpload = isRecording && myTeamMember !== undefined && myTeamMember.roleName !== null

    return (
        <main>
            <p className="links"><Link to={`/teams/${team.id}`}>← {team.name}</Link></p>
            <h1>{team.name} 산출물</h1>

            {canUpload && (
                <DeliverableUploadForm
                    roleName={myTeamMember.roleName}
                    onFileChange={(file) => setSelectedFile(file)}
                    onUpload={handleUpload}
                    uploading={uploading || deleting}
                    inputKey={fileInputKey}
                />
            )}
            {isRecording && myTeamMember && myTeamMember.roleName === null && <p className="notice">역할이 배정되면 산출물을 올릴 수 있습니다.</p>}
            {!isRecording && <p className="notice">평가 단계입니다. 산출물을 더 올리거나 삭제하거나 완료확인할 수 없습니다.</p>}
            {actionError && <p className="error">{actionError}</p>}

            <h2>올린 산출물 (역할별 최신순)</h2>
            <DeliverableList
                deliverables={deliverables}
                currentMemberId={me.id}
                canDelete={isRecording}
                onDownload={handleDownload}
                onDelete={handleDelete}
                downloadingId={downloadingId}
                deleteDisabled={uploading || deleting}
                canConfirm={isRecording && myTeamMember !== undefined}
                confirmationTargetId={confirmationTargetId}
                confirmations={confirmations}
                onOpenConfirmations={handleOpenConfirmations}
                onCloseConfirmations={handleCloseConfirmations}
                onConfirm={handleConfirm}
                confirmationBusy={confirmationBusy}
            />
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
