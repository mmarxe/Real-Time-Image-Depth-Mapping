# Real-Time Depth Mapping using CNNs

This is an adaptation of the AnyNet Deep Learning Model to create real time depth maps from mobile devices using CameraX and PyTorch-Lite.

# Installation

Download source code and open it using Android Studio and run.

```
git clone https://github.com/macsakini/Real-Time-Depth-Map-Application/tree/main
``` 

# Model

This implementation has been done on android and it used PyTorch lite model (PTL format). For other platforms, we have attached other model formats including TensorFlow, TensorFlow Lite and ONNX. The training notebook has also been included and is available from this link:

[Model Implementation](https://github.com/macsakini/Real-Time-Image-Depth-Mapping/blob/main/ptlmodel.ipynb)

Other Model Formats

[TensorFlow, TF-Lite and ONXX Formats](https://github.com/macsakini/Real-Time-Depth-Map-Application/tree/main/models)


# Demo

<img src="https://github.com/macsakini/Real-Time-Depth-Map-Application/blob/main/screenshots/ss1.jpeg?raw=true" alt="drawing" height="400"/>


# Citation
```
@article{wang2018anytime,
  title={Anytime Stereo Image Depth Estimation on Mobile Devices},
  author={Wang, Yan and Lai, Zihang and Huang, Gao and Wang, Brian H. and Van Der Maaten, Laurens and Campbell, Mark and Weinberger, Kilian Q},
  journal={arXiv preprint arXiv:1810.11408},
  year={2018}
}
```