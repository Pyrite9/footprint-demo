import ContributionDetail from './ContributionDetail'
import DeliverableDetail from './DeliverableDetail'
import DetailPanel from './DetailPanel'
import EvidencePanel from './EvidencePanel'

// 오른쪽 상세에 근거 패널을 그립니다. 처음에는 한 조원의 기록 목록이고, 기록을 누르면 그 기록의 상세입니다. 평가 단계의 화면에서 쓰므로 쓰기 버튼은 나오지 않습니다. 데이터와 동작은 page에서 props로 받습니다.
// isMember: 조원이 보면 true, 교수가 보면 false입니다.
function EvidenceSidePanel({ targetName, evidence, evidenceError, teamDeliverables, selectedRecord, recordConfirmations, currentMemberId, isMember, onClose, onSelectRecord, onBack, onDownload, downloading }) {
    if (selectedRecord === null) {
        return (
            <DetailPanel title={`${targetName}의 근거 기록`} onClose={onClose}>
                {evidenceError && <p className="sub">{evidenceError}</p>}
                {!evidenceError && evidence === null && <p className="sub">불러오는 중...</p>}
                {evidence !== null && (
                    <EvidencePanel
                        evidence={evidence}
                        teamDeliverables={teamDeliverables}
                        onSelectDeliverable={(deliverable) => onSelectRecord('deliverable', deliverable)}
                        onSelectContribution={(contribution) => onSelectRecord('contribution', contribution)}
                    />
                )}
            </DetailPanel>
        )
    }

    if (selectedRecord.type === 'deliverable') {
        return (
            <DetailPanel title={selectedRecord.record.fileName} onClose={onClose}>
                <div><button type="button" className="btn" onClick={onBack}>← 근거 기록으로</button></div>
                <DeliverableDetail
                    deliverable={selectedRecord.record}
                    deliverables={teamDeliverables}
                    currentMemberId={currentMemberId}
                    isMember={isMember}
                    isRecording={false}
                    onDownload={onDownload}
                    downloading={downloading}
                    confirmations={recordConfirmations}
                />
            </DetailPanel>
        )
    }

    return (
        <DetailPanel title={selectedRecord.record.title} onClose={onClose}>
            <div><button type="button" className="btn" onClick={onBack}>← 근거 기록으로</button></div>
            <ContributionDetail
                contribution={selectedRecord.record}
                currentMemberId={currentMemberId}
                isMember={isMember}
                isRecording={false}
                isRecordingLeader={false}
                confirmations={recordConfirmations}
            />
        </DetailPanel>
    )
}

export default EvidenceSidePanel
