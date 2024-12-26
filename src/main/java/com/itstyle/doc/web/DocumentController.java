package com.itstyle.doc.web;
import com.itstyle.doc.model.Attachment;
import com.itstyle.doc.model.Books;
import com.itstyle.doc.model.Documents;
import com.itstyle.doc.repository.AttachmentRepository;
import com.itstyle.doc.repository.BooksRepository;
import com.itstyle.doc.repository.DocumentsRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/docs")
public class DocumentController {

	@Autowired
	private BooksRepository booksRepository;

	@Autowired
	private DocumentsRepository documentsRepository;

	@Autowired
	private AttachmentRepository attachmentService;

//	@Autowired
//	private Conf conf;

//	@Autowired
//	private FileUtils fileService;

//	@Autowired
//	private Utils utils;

//	@Autowired
//	private Blackfriday blackfriday;

	private boolean enableAnonymous = true; // 需要根据实际配置设置

	// 其他依赖注入...

	// 处理文档首页请求
	@GetMapping("/{identify}")
	public String index(@PathVariable String identify, ModelMap model) {
		if (identify == null || identify.isEmpty()) {
			return "error/404";
		}

		if (!enableAnonymous && !isUserLoggedIn()) {
			return "redirect:/login";
		}

		Books bookResult = booksRepository.findByIdentify(identify);

		model.addAttribute("TplName", "document/" + bookResult.getTheme() + "_read");

		if (bookResult.isUseFirstDocument()) {
			Documents doc = documentsRepository.findFirstDocumentByBookId(bookResult.getBookId());
			if (doc != null) {
				model.addAttribute("Title", doc.getDocumentName());
				model.addAttribute("Content", doc.getRelease());
				model.addAttribute("Description", utils.autoSummary(doc.getRelease(), 120));
			}
		} else {
			model.addAttribute("Title", "概要");
			model.addAttribute("Content", blackfriday.run(bookResult.getDescription().getBytes()));
		}

		String tree = documentsRepository.createDocumentTreeForHtml(bookResult.getBookId(), 0);
		if (tree == null) {
			return "error/404";
		}
		model.addAttribute("Result", tree);

		model.addAttribute("Model", bookResult);

		return "document/index";
	}

	// 处理阅读文档请求
	@GetMapping("/{identify}/read/{id}")
	public String read(@PathVariable String identify, @PathVariable Long id, ModelMap model) {
		if (identify == null || identify.isEmpty() || id == null) {
			return "error/404";
		}

		if (!enableAnonymous && !isUserLoggedIn()) {
			return "redirect:/login";
		}

		Books bookResult = booksRepository.findByIdentify(identify);

		Documents doc = documentsRepository.findById(id);
		if (doc == null || doc.getBookId() != bookResult.getBookId()) {
			return "error/404";
		}

		model.addAttribute("DocumentId", id);
		model.addAttribute("Title", doc.getDocumentName());
		model.addAttribute("Content", doc.getRelease());
		model.addAttribute("Description", utils.autoSummary(doc.getRelease(), 120));

		String tree = documentsRepository.createDocumentTreeForHtml(bookResult.getBookId(), doc.getDocumentId());
		model.addAttribute("Result", tree);

		if (isAjax()) {
			Map<String, Object> data = new HashMap<>();
			data.put("doc_title", doc.getDocumentName());
			data.put("body", doc.getRelease());
			data.put("title", doc.getDocumentName() + " - Powered by MinDoc");
			data.put("version", doc.getVersion());
			return new ResponseEntity<>(data, HttpStatus.OK);
		}

		return "document/read";
	}

	// 处理编辑文档请求
	@GetMapping("/{identify}/edit")
	public String edit(@PathVariable String identify, ModelMap model) {
		if (identify == null || identify.isEmpty()) {
			return "error/404";
		}

		Books bookResult = booksRepository.findByIdentify(identify);

		String editor = bookResult.getEditor();
		String tplName;
		if ("markdown".equals(editor)) {
			tplName = "document/markdown_edit_template";
		} else if ("html".equals(editor)) {
			tplName = "document/new_html_edit_template";
		} else {
			tplName = "document/" + editor + "_edit_template";
		}

		model.addAttribute("TplName", tplName);
		model.addAttribute("Model", bookResult);

		List<DocumentTree> trees = documentsRepository.findDocumentTree(bookResult.getBookId());
		model.addAttribute("Result", trees);

		model.addAttribute("BaiDuMapKey", conf.getBaidumapkey());
		model.addAttribute("UploadFileSize", conf.getUploadFileSize() > 0 ? conf.getUploadFileSize() : "undefined");

		return "document/edit";
	}

	// 处理创建文档请求
	@PostMapping("/create")
	public ResponseEntity<?> create(@RequestParam String identify,
									@RequestParam String docIdentify,
									@RequestParam String docName,
									@RequestParam(required = false) Integer parentId,
									@RequestParam(required = false) Integer docId,
									@RequestParam(required = false) Integer isOpen) {
		if (identify == null || identify.isEmpty()) {
			return ResponseEntity.badRequest().body("参数错误");
		}

		if (docName == null || docName.isEmpty()) {
			return ResponseEntity.badRequest().body("文档名称不能为空");
		}

		int bookId;
		if (isAdmin()) {
			Books book = booksRepository.findByIdentify(identify);
			if (book == null) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("项目不存在或权限不足");
			}
			bookId = book.getBookId();
		} else {
			Books bookResult = booksRepository.findByIdentify(identify);
			if (bookResult == null || bookResult.getRoleId() == conf.getBookObserver()) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("项目不存在或权限不足");
			}
			bookId = bookResult.getBookId();
		}

		if (docIdentify != null && !docIdentify.matches("[a-z]+[a-zA-Z0-9_.\\-]*$")) {
			return ResponseEntity.badRequest().body("文档标识只能包含小写字母、数字，以及“-”、“.”和“_”符号");
		}

		if (docIdentify != null && documentsRepository.existsByIdentify(docIdentify, bookId)) {
			return ResponseEntity.badRequest().body("文档标识已被使用");
		}

		if (parentId != null && parentId > 0) {
			Optional<Documents> parentDoc = documentsRepository.findById(parentId);
			if (parentDoc == null || parentDoc.getBookId() != bookId) {
				return ResponseEntity.badRequest().body("父分类不存在");
			}
		}

		Documents document = new Documents();
		document.setMemberId(getMemberId());
		document.setBookId(bookId);
		document.setIdentify(docIdentify);
		document.setVersion((int) System.currentTimeMillis());
		document.setDocumentName(docName);
		document.setParentId(parentId);
		//document.setIsOpen(isOpen != null ? isOpen : 0);

		//documentService.saveOrUpdate(document);

		return ResponseEntity.ok(document);
	}

	// 处理上传附件或图片请求
	@PostMapping("/upload")
	public ResponseEntity<?> upload(@RequestParam String identify,
									@RequestParam Long docId,
									@RequestParam(required = false) MultipartFile file) {
		if (identify == null || identify.isEmpty()) {
			return ResponseEntity.badRequest().body("参数错误");
		}

		String fileName = file.getOriginalFilename();
		if (fileName == null || fileName.isEmpty()) {
			return ResponseEntity.badRequest().body("没有发现需要上传的文件");
		}

		String ext = FilenameUtils.getExtension(fileName);
		if (!conf.isAllowUploadFileExt(ext)) {
			return ResponseEntity.badRequest().body("不允许的文件类型");
		}

		int bookId;
		if (isAdmin()) {
			Books book = booksRepository.findByIdentify(identify);
			if (book == null) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("项目不存在或权限不足");
			}
			bookId = book.getBookId();
		} else {
			Books bookResult = booksRepository.findByIdentify(identify);
			if (bookResult == null || bookResult.getRoleId() != conf.getBookEditor()
					&& bookResult.getRoleId() != conf.getBookAdmin()
					&& bookResult.getRoleId() != conf.getBookFounder()) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("权限不足");
			}
			bookId = bookResult.getBookId();
		}

		Documents doc = documentsRepository.findById(docId);
		if (doc == null || doc.getBookId() != bookId) {
			return ResponseEntity.badRequest().body("文档不属于指定的项目");
		}

		String filePath = fileService.saveFile(file, identify, docId);
		if (filePath == null) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("保存文件失败");
		}

		Attachment attachment = new Attachment();
		attachment.setBookId(bookId);
		attachment.setFileName(fileName);
		attachment.setFileExt(ext);
		attachment.setFilePath(filePath);
		attachment.setDocumentId(docId);
		attachment.setFileSize(file.getSize());

		attachmentService.save(attachment);

		Map<String, Object> data = new HashMap<>();
		data.put("errcode", 0);
		data.put("success", 1);
		data.put("message", "ok");
		data.put("url", conf.urlForNotHost("DocumentController.downloadAttachment", ":key", identify, ":attach_id", attachment.getAttachmentId()));
		data.put("alt", fileName);
		data.put("is_attach", true);
		data.put("attach", attachment);

		return ResponseEntity.ok(data);
	}
*//*


	// 处理下载附件请求
	@GetMapping("/{identify}/download/{attachId}")
	public void downloadAttachment(@PathVariable String identify,
								   @PathVariable Long attachId,
								   HttpServletResponse response) throws IOException {
		if (!isAdmin() && !isReadable(identify, null).isDownload()) {
			return;
		}

		Attachment attachment = attachmentService.findById(attachId);
		if (attachment == null || attachment.getBookId() != booksRepository.findByIdentify(identify).getBookId()) {
			return;
		}

		String filePath = attachment.getFilePath();
		if (filePath == null || !new File(filePath).exists()) {
			return;
		}

		response.setHeader("Content-Disposition", "attachment; filename=\"" + attachment.getFileName() + "\"");
		response.setContentType("application/octet-stream");

		Files.copy(Paths.get(filePath), response.getOutputStream());
	}

	// 辅助方法：检查用户是否登录
	private boolean isUserLoggedIn() {
		// 实现用户登录检查逻辑
		return false;
	}

	// 辅助方法：判断是否是AJAX请求
	private boolean isAjax() {
		// 实现AJAX请求检查逻辑
		return false;
	}


	// 辅助方法：判断是否是管理员
	private boolean isAdmin() {
		// 实现管理员检查逻辑
		return false;
	}

	// 辅助方法：获取当前用户ID
	private int getMemberId() {
		// 实现获取用户ID逻辑
		return 0;
	}
}