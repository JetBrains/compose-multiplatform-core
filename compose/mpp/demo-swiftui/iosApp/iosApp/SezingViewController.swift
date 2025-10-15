//
//  SezingViewController.swift
//  iosApp
//
//  Created by Andrei Salavei on 15.10.25.
//

import UIKit

class SezingViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()

        // Do any additional setup after loading the view.
    }


    /*
    // MARK: - Navigation

    // In a storyboard-based application, you will often want to do a little preparation before navigation
    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        // Get the new view controller using segue.destination.
        // Pass the selected object to the new view controller.
    }
    */

}


class WrapperView: UIView {
    var container: UIView? {
        didSet {
            guard let container = container else { return }
           
            container.addSubview(self)
            
            container.topAnchor.constraint(equalTo: self.topAnchor).isActive = true
            container.leadingAnchor.constraint(equalTo: self.leadingAnchor).isActive = true
            
            container.bottomAnchor.constraint(greaterThanOrEqualTo: self.bottomAnchor, constant: 123).isActive = true
            container.bottomAnchor.constraint(lessThanOrEqualTo: self.bottomAnchor, constant: 123).isActive = true

            container.trailingAnchor.constraint(greaterThanOrEqualTo: self.trailingAnchor, constant: 123).isActive = true
            container.trailingAnchor.constraint(lessThanOrEqualTo: self.trailingAnchor, constant: 123).isActive = true
        }
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        
        print(container?.frame)
    }
}

