package tw.niels.beverage_api_project.modules.file.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.file.dto.FileChunkUploadDto;
import tw.niels.beverage_api_project.modules.file.dto.FileMergeRequestDto;
import tw.niels.beverage_api_project.modules.file.service.FileService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.FILES)
@Tag(name = "File Management", description = "檔案上傳與管理 (支援分片)")
public class FileController {

    private final FileService fileService;
    private final ControllerHelperService helperService;

    public FileController(FileService fileService, ControllerHelperService helperService) {
        this.fileService = fileService;
        this.helperService = helperService;
    }

    @PostMapping(value = "/upload/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "上傳檔案分片", description = "將大檔案切分後逐一上傳")
    // 加上 @Valid：啟用 DTO 內的 fileId / chunkNumber 驗證(S-6)
    public ResponseEntity<String> uploadChunk(@Valid @ModelAttribute FileChunkUploadDto requestDto) {
        // 物件 key 以 brandId 為前綴，做租戶隔離
        fileService.uploadChunk(helperService.getCurrentBrandId(), requestDto);
        return ResponseEntity.ok("Chunk " + requestDto.getChunkNumber() + " uploaded.");
    }

    @PostMapping("/upload/merge")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "合併分片", description = "所有分片上傳完成後，呼叫此接口進行合併")
    public ResponseEntity<Map<String, String>> mergeChunks(@Valid @RequestBody FileMergeRequestDto requestDto) {
        String objectName = fileService.mergeChunks(helperService.getCurrentBrandId(), requestDto);
        String url = fileService.getFileUrl(objectName);

        // 修正：getFileUrl 失敗時會回傳 null，而 Map.of 不允許 null 值（會 NPE），
        // 改用 LinkedHashMap，且只在 url 非 null 時才放入。
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", "Merge successful");
        body.put("objectName", objectName);
        if (url != null) {
            body.put("url", url);
        }
        return ResponseEntity.ok(body);
    }
}
