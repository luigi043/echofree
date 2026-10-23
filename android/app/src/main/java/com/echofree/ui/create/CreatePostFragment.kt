package com.echofree.ui.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
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

        etContent.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val len = s?.length ?: 0
                tvCharCount.text = "$len / $maxChars"
                tvCharCount.setTextColor(
                    if (len > maxChars) requireContext().getColor(R.color.error)
                    else requireContext().getColor(R.color.on_surface_variant)
                )
                btnPost.isEnabled = len in 1..maxChars
            }
        })

        btnPost.setOnClickListener {
            val content = etContent.text.toString().trim()
            val tags    = etTags.text.toString()
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .take(5)    // cap tags to prevent spam
            val author  = etAuthor.text.toString().trim().ifEmpty { "Anonymous" }

            viewModel.createPost(content, tags, author)
        }

        viewModel.createState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CreateState.Loading -> btnPost.isEnabled = false
                is CreateState.Success -> findNavController().navigateUp()
                is CreateState.Error   -> {
                    btnPost.isEnabled = true
                    Snackbar.make(view, state.message, Snackbar.LENGTH_LONG).show()
                }
                null -> {}
            }
        }
    }
}
