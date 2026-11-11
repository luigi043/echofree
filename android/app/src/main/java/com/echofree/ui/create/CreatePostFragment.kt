package com.echofree.ui.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.echofree.R

/**
 * Post creation screen.
 *
 * Kept intentionally minimal:
 *  - Content field (required, 280 char limit)
 *  - Tags field (optional, comma-separated)
 *  - Author name (defaults to "Anonymous")
 *
 * No image uploads in v1 — keeps the playing field level regardless
 * of production value and focuses attention on written ideas.
 */
class CreatePostFragment : Fragment() {

    private val viewModel: CreatePostViewModel by viewModels()

    private lateinit var etContent:    EditText
    private lateinit var etTags:       EditText
    private lateinit var etAuthor:     EditText
    private lateinit var tvCharCount:  TextView
    private lateinit var btnPost:      MaterialButton

    private val maxChars = 280

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_create_post, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etContent   = view.findViewById(R.id.et_content)
        etTags      = view.findViewById(R.id.et_tags)
        etAuthor    = view.findViewById(R.id.et_author)
        tvCharCount = view.findViewById(R.id.tv_char_count)
        btnPost     = view.findViewById(R.id.btn_post)

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        fun updateControls() {
            val text = etContent.text.toString()
            val busy = viewModel.createState.value == CreateState.Loading
            tvCharCount.text = getString(R.string.character_count, text.length, maxChars)
            btnPost.isEnabled = !busy && text.isNotBlank() && text.length <= maxChars
            btnPost.setText(if (busy) R.string.publishing else R.string.publish)
            etContent.isEnabled = !busy
            etTags.isEnabled = !busy
            etAuthor.isEnabled = !busy
        }
        etContent.doAfterTextChanged { updateControls() }

        btnPost.setOnClickListener {
            val content = etContent.text.toString().trim()
            val tags    = etTags.text.toString()
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(5)    // cap tags to prevent spam
            val author  = etAuthor.text.toString().trim().ifEmpty { "Anonymous" }

            viewModel.createPost(content, tags, author)
        }

        val publishError = view.findViewById<TextView>(R.id.publish_error)
        viewModel.createState.observe(viewLifecycleOwner) { state ->
            publishError.visibility = if (state == CreateState.Error) View.VISIBLE else View.GONE
            updateControls()
            when (state) {
                CreateState.Success -> {
                    val nav = findNavController()
                    nav.previousBackStackEntry?.savedStateHandle?.set("post_published", true)
                    nav.navigateUp()
                }
                else -> Unit
            }
        }
    }
}
