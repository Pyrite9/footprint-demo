// Blob을 파일로 저장합니다. 임시 주소(object URL)를 만든 <a download>를 눌러 브라우저의 다운로드를 쓰고, 다 쓴 주소는 지웁니다.
export function saveBlob(blob, fileName) {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    URL.revokeObjectURL(url)
}
