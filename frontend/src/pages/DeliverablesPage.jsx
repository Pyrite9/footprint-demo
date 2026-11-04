import { useEffect, useState } from 'react'
import { useLocation, useParams } from 'react-router'
import { getMe } from '../api/auth'
import { cancelConfirmation, confirmDeliverable, getDeliverableConfirmations } from '../api/confirmations'
import { deleteDeliverable, downloadDeliverable, getDeliverables, uploadDeliverable } from '../api/deliverables'
import { getTeam, getTeamMembers } from '../api/teams'
import ConfirmBox from '../components/ConfirmBox'
import DeliverableDetail from '../components/DeliverableDetail'
import DeliverableList from '../components/DeliverableList'
import DeliverableTimeList from '../components/DeliverableTimeList'
import DeliverableUploadForm from '../components/DeliverableUploadForm'
import DetailPanel from '../components/DetailPanel'
import Icon from '../components/Icon'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import TopBar from '../components/TopBar'

// 조의 산출물 화면입니다. 목록과 다운로드, 완료확인 이력은 조원과 교수가, 올리기는 기록 단계에서 역할이 배정된 조원만, 삭제는 기록 단계에서 올린 조원만, 완료확인은 기록 단계에서 올린 조원을 뺀 조원이 합니다(서버가 확인합니다).
function DeliverablesPage() {
    const { teamId } = useParams()
    const location = useLocation()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [deliverables, setDeliverables] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    // 목록 보기 방식입니다. 'role'은 역할별, 'time'은 시간순입니다.
    const [view, setView] = useState('role')
    // 오른쪽 상세에 연 산출물의 번호입니다. 조 홈의 최근 산출물에서 왔으면 그 파일을 바로 엽니다.
    const [selectedId, setSelectedId] = useState(location.state?.selectedId ?? null)
    // 오른쪽에 올리기 form을 열어 두었으면 true입니다. 상세와 올리기 form은 한 번에 하나만 열립니다.
    const [uploadOpen, setUploadOpen] = useState(false)
    const [selectedFile, setSelectedFile] = useState(null)
    const [uploading, setUploading] = useState(false)
    const [downloadingId, setDownloadingId] = useState(null)
    // 삭제 확인 상자를 열어 두었으면 true입니다.
    const [confirmingDelete, setConfirmingDelete] = useState(false)
    // 삭제와 올리기는 서로 끝날 때까지 막습니다. 동시에 끝나면 나중에 끝난 쪽이 옛 deliverables로 덮어써 먼저 끝난 결과가 사라지기 때문입니다.
    const [deleting, setDeleting] = useState(false)
    const [confirmations, setConfirmations] = useState([])
    // 완료확인과 확인 취소는 서로 끝날 때까지 막습니다.
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

    // 산출물을 열면 그 산출물의 완료확인 이력을 불러옵니다.
    useEffect(() => {
        if (selectedId === null) return undefined
        // 다른 줄을 빠르게 누르면 늦게 온 옛 응답이 이력을 덮어쓸 수 있습니다. 줄이 바뀌면 이전 요청의 결과를 버립니다.
        let ignore = false
        getDeliverableConfirmations(selectedId)
            .then((data) => {
                if (!ignore) setConfirmations(data)
            })
            .catch((err) => {
                if (!ignore) setActionError(err.message)
            })
        return () => {
            ignore = true
        }
    }, [selectedId])

    // 산출물을 오른쪽 상세에 엽니다. 다른 줄의 이력과 확인 상자는 지웁니다.
    function handleSelect(deliverableId) {
        setActionError(null)
        setSelectedId(deliverableId)
        setConfirmations([])
        setConfirmingDelete(false)
        setUploadOpen(false)
    }

    // 오른쪽에 올리기 form을 엽니다.
    function handleOpenUpload() {
        setActionError(null)
        setSelectedId(null)
        setUploadOpen(true)
    }

    // 올리기 form을 닫고 고른 파일을 지웁니다.
    function handleCloseUpload() {
        setUploadOpen(false)
        setSelectedFile(null)
    }

    // 고른 파일을 올립니다. 성공하면 새 산출물을 목록 맨 위(최신)에 더하고 form을 닫습니다.
    function handleUpload() {
        setActionError(null)
        setUploading(true)
        uploadDeliverable(teamId, selectedFile)
            .then((created) => {
                setDeliverables([created, ...deliverables])
                handleCloseUpload()
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setUploading(false))
    }

    // 산출물을 삭제합니다. 성공하면 그 줄만 서버가 돌려준 값으로 바꿉니다. 목록에서 빼지 않습니다.
    function handleDelete(target) {
        setActionError(null)
        setDeleting(true)
        deleteDeliverable(target.id)
            .then((updated) => {
                setDeliverables(deliverables.map((deliverable) => (deliverable.id === updated.id ? updated : deliverable)))
                setConfirmingDelete(false)
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setDeleting(false))
    }

    // 열어 둔 산출물을 확인합니다. 성공하면 새 확인을 이력 맨 위(최근)에 더합니다.
    function handleConfirm() {
        setActionError(null)
        setConfirmationBusy(true)
        confirmDeliverable(selectedId)
            .then((created) => setConfirmations([created, ...confirmations]))
            .catch((err) => setActionError(err.message))
            .finally(() => setConfirmationBusy(false))
    }

    // 내 확인을 취소합니다. 성공하면 그 줄만 서버가 돌려준 값으로 바꿉니다. 이력에서 빼지 않습니다.
    function handleCancelConfirmation(target) {
        setActionError(null)
        setConfirmationBusy(true)
        cancelConfirmation(target.id)
            .then((updated) => {
                setConfirmations(confirmations.map((confirmation) => (confirmation.id === updated.id ? updated : confirmation)))
            })
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

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    // 내 조 참여 기록입니다. 교수는 조원이 아니라서 없습니다(undefined). 기록 단계이고 내 역할이 있을 때만 올리기 버튼을 보여 줍니다.
    const myTeamMember = members.find((member) => member.memberId === me.id)
    const isRecording = team.phase === 'RECORDING'
    const canUpload = isRecording && myTeamMember !== undefined && myTeamMember.roleName !== null
    const selectedDeliverable = deliverables.find((deliverable) => deliverable.id === selectedId)

    return (
        <>
            <TopBar crumbs={[team.assignmentTitle, team.name, '산출물']}>
                {canUpload && <button type="button" className="btn primary" onClick={handleOpenUpload}><Icon name="upload" />파일 올리기</button>}
            </TopBar>
            {isRecording && myTeamMember && myTeamMember.roleName === null && <div className="lockband">역할이 배정되면 산출물을 올릴 수 있습니다.</div>}
            {!isRecording && <div className="lockband">평가 단계입니다. 산출물을 더 올리거나 삭제하거나 완료확인할 수 없습니다.</div>}
            {actionError && <div className="errband" role="alert">{actionError}</div>}
            <div className={selectedDeliverable || uploadOpen ? 'main-row detail-open' : 'main-row'}>
                <main className="content">
                    <div className="page-head">
                        <h1>산출물</h1>
                        <div className="tabs">
                            <button type="button" className={view === 'role' ? 'tab on' : 'tab'} onClick={() => setView('role')}>역할별</button>
                            <button type="button" className={view === 'time' ? 'tab on' : 'tab'} onClick={() => setView('time')}>시간순</button>
                        </div>
                    </div>
                    {deliverables.length === 0 && (
                        <section className="card empty">
                            <b>아직 올린 파일이 없습니다</b>
                            <p className="sub">역할이 정해진 조원이 올리면 여기에 나옵니다.</p>
                        </section>
                    )}
                    {deliverables.length > 0 && view === 'role' && (
                        <DeliverableList
                            deliverables={deliverables}
                            members={members}
                            uploadRoleName={canUpload ? myTeamMember.roleName : null}
                            selectedId={selectedId}
                            onSelect={handleSelect}
                            onUpload={handleOpenUpload}
                        />
                    )}
                    {deliverables.length > 0 && view === 'time' && (
                        <DeliverableTimeList deliverables={deliverables} selectedId={selectedId} onSelect={handleSelect} />
                    )}
                </main>
                {uploadOpen && (
                    <DetailPanel title="파일 올리기" onClose={handleCloseUpload}>
                        <DeliverableUploadForm
                            roleName={myTeamMember.roleName}
                            selectedFile={selectedFile}
                            onFileChange={setSelectedFile}
                            onUpload={handleUpload}
                            uploading={uploading || deleting}
                        />
                    </DetailPanel>
                )}
                {selectedDeliverable && (
                    <DetailPanel title={selectedDeliverable.fileName} onClose={() => setSelectedId(null)}>
                        {confirmingDelete && (
                            <ConfirmBox
                                title="이 파일을 삭제할까요?"
                                body={'파일은 되돌릴 수 없고, 올린 기록은 "삭제됨"으로 남습니다.'}
                                okLabel="삭제"
                                okClass="danger"
                                onOk={() => handleDelete(selectedDeliverable)}
                                onCancel={() => setConfirmingDelete(false)}
                                busy={uploading || deleting}
                            />
                        )}
                        <DeliverableDetail
                            deliverable={selectedDeliverable}
                            deliverables={deliverables}
                            currentMemberId={me.id}
                            isMember={myTeamMember !== undefined}
                            isRecording={isRecording}
                            onDownload={handleDownload}
                            downloading={downloadingId === selectedDeliverable.id}
                            onDelete={() => setConfirmingDelete(true)}
                            confirmations={confirmations}
                            onConfirm={handleConfirm}
                            onCancelConfirmation={handleCancelConfirmation}
                            confirmationBusy={confirmationBusy}
                        />
                    </DetailPanel>
                )}
            </div>
        </>
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
