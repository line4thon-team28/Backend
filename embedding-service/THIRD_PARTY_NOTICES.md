# Third-party notices

This service performs local inference. It does not redistribute model weights in Git and does not invoke a paid inference API. Package/model license permission does not establish permission to use arbitrary input photographs.

## Model

- Model: `google/siglip-base-patch16-224`
- Revision: `7fd15f0689c79d79e38b1c2e2e2370a7bf2761ed`
- License declared by the model repository: Apache-2.0
- Source: https://huggingface.co/google/siglip-base-patch16-224/tree/7fd15f0689c79d79e38b1c2e2e2370a7bf2761ed
- Original implementation: https://github.com/google-research/big_vision
- The pinned model repository has no separate LICENSE or NOTICE file. `download_model.py` retains its original README alongside downloaded weights. The Apache-2.0 license text is included in `licenses/Apache-2.0.txt`.
- We do not modify the upstream model files. RGB conversion, EXIF orientation handling and feature normalization are performed by our adapter. The adapter's version identifier records this preprocessing separately.

## Runtime packages

| Package | Version | Upstream license/source |
|---|---|---|
| torch | 2.11.0, CPU wheel | BSD-style license and bundled third-party notices; https://github.com/pytorch/pytorch/tree/v2.11.0 |
| transformers | 4.57.6 | Apache-2.0; https://github.com/huggingface/transformers/tree/v4.57.6 |
| Pillow | 13.0.0 | MIT-CMU and bundled third-party notices; https://github.com/python-pillow/Pillow/tree/13.0.0 |
| safetensors | 0.8.0 | Apache-2.0; https://github.com/huggingface/safetensors/tree/v0.8.0 |
| huggingface-hub | 0.36.2 | Apache-2.0; https://github.com/huggingface/huggingface_hub/tree/v0.36.2 |
| tokenizers | 0.22.2 | Apache-2.0; https://github.com/huggingface/tokenizers/tree/v0.22.2 |

Original license files and the torch NOTICE from the tested distributions are preserved in `licenses/`. Exact tested dependency versions, including transitive dependencies, are in `requirements-lock.txt`. Installed distributions also retain their own license/notice files; preserve those files when redistributing an environment or container. Source links and the model version must accompany any future model redistribution.

Test images in `tests/` and `smoke_model.py` are generated from scratch in memory. No web-scraped animal photograph or third-party image dataset is bundled. Real-photo evaluation and public API image usage must follow the relevant data provider's terms.
