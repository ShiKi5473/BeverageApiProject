package tw.niels.beverage_api_project.modules.file.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.file.dto.FileChunkUploadDto;
import tw.niels.beverage_api_project.modules.file.dto.FileMergeRequestDto;
import tw.niels.beverage_api_project.modules.file.service.FileService;

import java.util.Map;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.FILES)
@Tag(name = "File Management", description = "檔案上傳與管理 (支援分片)")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "上傳檔案分片", description = "將大檔案切分後逐一上傳")
    public ResponseEntity<String> uploadChunk(@ModelAttribute FileChunkUploadDto requestDto) {
        fileService.uploadChunk(requestDto);
        return ResponseEntity.ok("Chunk " + requestDto.getChunkNumber() + " uploaded.");
    }

    @PostMapping("/upload/merge")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "合併分片", description = "所有分片上傳完成後，呼叫此接口進行合併")
    public ResponseEntity<Map<String, String>> mergeChunks(@RequestBody FileMergeRequestDto requestDto) {
        String objectName = fileService.mergeChunks(requestDto);
        String url = fileService.getFileUrl(objectName);

        return ResponseEntity.ok(Map.of(
                "message", "Merge successful",
                "objectName", objectName,
                "url", url
        ));
    }
}